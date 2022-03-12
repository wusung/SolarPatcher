/*
 * Solar Patcher, a runtime patcher for Lunar Client
 * Copyright (C) 2022 Solar Tweaks and respective contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.grappenmaker.solarpatcher.modules

import com.grappenmaker.solarpatcher.asm.*
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchName
import com.grappenmaker.solarpatcher.asm.method.InvocationType
import com.grappenmaker.solarpatcher.asm.method.MethodDescription
import com.grappenmaker.solarpatcher.asm.transform.ClassTransform
import com.grappenmaker.solarpatcher.asm.util.getField
import com.grappenmaker.solarpatcher.asm.util.invokeMethod
import com.grappenmaker.solarpatcher.config.Constants
import com.grappenmaker.solarpatcher.util.componentName
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
import java.lang.reflect.Modifier
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// Utility "module" to get runtime data
// Internally used to lookup classes and methods that can't be recognized with simple analysis
object RuntimeData : Module() {
    private val knownData = mutableMapOf<String, Any?>()
    private val finders = mutableListOf<Finder<*>>()

    // Classnodes that have been found
    val lunarClientMain by +FindClass({ node ->
        loadRuntimeValues(node)

        // Get assets socket field
        val call = node.methods.filter { it.constants.contains("Connected to the AssetServer") }
            .mapNotNull { it.calls.find { c -> c.name == "connect" } }.first()

        val insn = call.previous as FieldInsnNode
        assetsSocketField = FieldDescription(insn.name, insn.desc, insn.owner)
    }) { it.hasConstant("Starting Lunar client...") }

    val renderer by +FindClass { it.calls { c -> c.name == "bridge\$enableColorMaterial" } }
    lateinit var playerEntityBridge: ClassNode

    // Class names that have been found
    lateinit var outgoingChatEvent: String // not internal name!
    lateinit var skinLayerClass: String

    // Method nodes that have been found
    val sendPopupMethod by +FindMethod { it.method.hasConstant("\u0001[\u0001\u0001\u0001] \u0001\u0001") }
    val getPlayerMethod by +findBridgeMethod("bridge\$getPlayer")
    val getPlayerNameMethod by +findBridgeMethod("bridge\$getName") { (owner) -> owner.methods.any { it.name == "bridge\$getGameProfile" } }
    val isWindowFocusedMethod by +findBridgeMethod("bridge\$isWindowFocused")
    val displayMessageMethod by +findBridgeMethod("bridge\$addChatMessage", { (owner) -> playerEntityBridge = owner })
    val renderPlayerItemsMethod by +FindMethod {
        it.method.name == "renderPlayerItems" &&
                it.owner.name == Constants.playerConfigurationsName
    }

    // Method descriptions that have been found
    lateinit var getDisplayToIPMapMethod: MethodDescription
    lateinit var getServerMappingsMethod: MethodDescription
    lateinit var getLunarmainMethod: MethodDescription
    lateinit var getClientBridgeMethod: MethodDescription
    lateinit var getServerDataMethod: MethodDescription
    lateinit var toBridgeComponentMethod: MethodDescription
    lateinit var renderLayerMethod: MethodDescription
    lateinit var shouldRenderLayerMethod: MethodDescription

    // Field descriptions that have been found
    lateinit var assetsSocketField: FieldDescription

    // Available runtime data
    val version by runtimeValue("version", "unknown")
    val os by runtimeValue("os", "unknown")
    val arch by runtimeValue("arch", "unknown")

    // Finders that do not need to be stored
    init {
        +FindClass({ node ->
            node.methods.find { m -> m.name == "<init>" }?.let { m ->
                val type = m.instructions.filterIsInstance<LdcInsnNode>()
                    .lastOrNull { it.cst is Type }?.cst as Type? ?: return@FindClass
                outgoingChatEvent = type.className
            }
        }) { it.constants.containsAll(listOf("/lc_upload_screenshot", "Screenshot taken")) }

        +FindMethod({
            getClientBridgeMethod = it.method.calls.first().asDescription()
            getServerDataMethod = it.method.calls[1].asDescription()
        }) { it.method.calls(matchName("getLunarServer")) && it.owner.calls(matchName("bridge\$getPlayer")) }

        +FindMethod({ (_, method) ->
            toBridgeComponentMethod = method.calls
                .find {
                    Type.getArgumentTypes(it.desc).firstOrNull()?.internalName == componentName &&
                            Type.getReturnType(it.desc).internalName.startsWith("lunar/")
                }!!.asDescription()
        }) { it.method.hasConstant(" [x\u0001]") }

        +FindMethod({ (node, renderDelegate) ->
            val renderCall = renderDelegate.calls.last()
            renderLayerMethod = renderCall.asDescription()

            val shouldRenderDelegate = node.methods.find { it.desc == "()Z" } ?: error("No shouldRender was found")
            val shouldRenderCall = shouldRenderDelegate.calls.last()
            shouldRenderLayerMethod = shouldRenderCall.asDescription()

            skinLayerClass = shouldRenderLayerMethod.owner
        }) {
            !it.owner.isInterface && !it.owner.isAbstract && it.method.name == "bridge\$render"
                    && it.method.desc.endsWith("FFFFFFF)V")
                    && it.owner.methods.find { m -> m.desc == "()Z" }?.calls?.isNotEmpty() == true
                    && it.method.calls.isNotEmpty()
        }

        +FindClass({ node ->
            val mainNode = lunarClientMain ?: return@FindClass
            getServerMappingsMethod =
                mainNode.methods.find { Type.getReturnType(it.desc).internalName == node.name }?.asDescription(mainNode)
                    ?: error("Impossible?")

            val remapServerMethod = node.methods.find {
                Type.getArgumentTypes(it.desc).getOrNull(0)?.internalName == internalString
                        && Type.getReturnType(it.desc).internalName == internalString
            }!!

            getDisplayToIPMapMethod = remapServerMethod.calls.first().asDescription()
        }) { it.hasConstant("https://servermappings.lunarclientcdn.com/servers.json") }
    }

    @Transient
    override val isEnabled = true
    override fun generate(node: ClassNode): ClassTransform? {
        // Find the requested classes and methods
        finders.asSequence().filter { it.value == null }.forEach { finder ->
            when (finder) {
                is FindClass -> if (finder.matcher(node)) finder.value = node
                is FindMethod -> finder.value = node.methods.map { MethodInfo(node, it) }.find(finder.matcher)
            }
        }

        return null // Dont perform transformation
    }

    // Function for pulling runtime values (such as versions)
    private fun loadRuntimeValues(node: ClassNode) {
        println("Loading runtime values")

        try {
            getLunarmainMethod =
                node.methods.find {
                    Type.getReturnType(it.desc).internalName == node.name
                            && Modifier.isStatic(it.access)
                }?.asDescription(node) ?: error("No get lunar main method?")

            val clinit = node.methods.find { it.name == "<clinit>" }!!
            val assignments = clinit.instructions
                .filterIsInstance<FieldInsnNode>()
                .filter { it.opcode == PUTSTATIC }
                .associate { it.name to (it.previous as? LdcInsnNode)?.cst }

            knownData += node.fields
                .associate { it.name to (it.value ?: assignments[it.name]) }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Couldn't load runtime values!")
        }

        // Debuy log information
        println("Runtime information: ${knownData.toList()}")
    }

    // Stuff for actually finding the classes and methods we need
    private sealed class Finder<T>(
        val onFound: (T) -> Unit,
        val matcher: (T) -> Boolean
    ) : ReadOnlyProperty<RuntimeData, T?> {
        var value: T? = null
            set(value) {
                if (value != null) {
                    onFound(value)
                    field = value
                }
            }

        override fun getValue(thisRef: RuntimeData, property: KProperty<*>): T? = value
    }

    private fun findBridgeMethod(
        name: String,
        onFound: (MethodInfo) -> Unit = {},
        extraCondition: (MethodInfo) -> Boolean = { true }
    ) = FindMethod(onFound) { it.owner.isInterface && it.method.name == name && extraCondition(it) }

    private class FindClass(
        onFound: (ClassNode) -> Unit = {},
        matcher: (ClassNode) -> Boolean
    ) : Finder<ClassNode>(onFound, matcher)

    private class FindMethod(
        onFound: (MethodInfo) -> Unit = {},
        matcher: (MethodInfo) -> Boolean
    ) : Finder<MethodInfo>(onFound, matcher)

    private operator fun <T> Finder<T>.unaryPlus(): Finder<T> {
        finders.add(this)
        return this
    }

    data class MethodInfo(val owner: ClassNode, val method: MethodNode) {
        fun asDescription() = method.asDescription(owner)
    }

    private inline fun <reified T> runtimeValue(field: String, default: T) =
        lazy { (knownData[field] ?: default) as? T ?: error("Wrong type in runtime data!") }
}

// Utility to get the lunar client main instance
fun MethodVisitor.getLunarMain() =
    invokeMethod(InvocationType.STATIC, RuntimeData.getLunarmainMethod)

// Utility to get the assets socket
fun MethodVisitor.getAssetsSocket() {
    getLunarMain()
    getField(RuntimeData.assetsSocketField, static = false)
}

// Utility to load the client bridge onto the stack
fun MethodVisitor.getClientBridge() =
    invokeMethod(InvocationType.STATIC, RuntimeData.getClientBridgeMethod)

// Utility to load the current server data onto the stack
fun MethodVisitor.getServerData() {
    getClientBridge()
    invokeMethod(InvocationType.INTERFACE, RuntimeData.getServerDataMethod)
}

// Utility to load server mappings class onto the stack
fun MethodVisitor.getServerMappings() {
    getLunarMain()
    invokeMethod(InvocationType.VIRTUAL, RuntimeData.getServerMappingsMethod)
}

// Utility to load the boolean if the client window is focused onto the stack
fun MethodVisitor.isWindowFocused() {
    getClientBridge()
    val windowFocusedMethod = RuntimeData.isWindowFocusedMethod ?: error("Window focused method has not been found")
    invokeMethod(InvocationType.INTERFACE, windowFocusedMethod.asDescription())
}

// Utility to convert a kyori adventure compoenent that is currently on the stack
// to a lunar bridge component
fun MethodVisitor.toBridgeComponent() =
    invokeMethod(InvocationType.STATIC, RuntimeData.toBridgeComponentMethod)

// Utility to load the current player bridge
fun MethodVisitor.getPlayerBridge() {
    getClientBridge()

    val playerMethod = RuntimeData.getPlayerMethod ?: error("Get player method was not found")
    invokeMethod(InvocationType.INTERFACE, playerMethod.asDescription())
}