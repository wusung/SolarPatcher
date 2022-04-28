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
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
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

    val textModClass by +FindClass { node ->
        node.hasConstant("[\u0001]") && node.methods.any { m ->
            val args = Type.getArgumentTypes(m.desc)
            args.size == 4 && args[0].internalName.startsWith("lunar/") &&
                    args[1].internalName == internalString &&
                    args.drop(2).all { it == Type.FLOAT_TYPE } &&
                    m.access == 0x4
        }
    }

    val renderer by +FindClass { it.calls { c -> c.name == "bridge\$enableColorMaterial" } }
    lateinit var playerEntityBridge: ClassNode

    // Class names that have been found
    lateinit var outgoingChatEvent: String // not internal name!
    lateinit var skinLayerClass: String
    var featureDetailsClass: String? = null

    // Method nodes that have been found
    val sendPopupMethod by +FindMethod { it.method.hasConstant("\u0001[\u0001\u0001\u0001] \u0001\u0001") }
    val getPlayerMethod by +findBridgeMethod("bridge\$getPlayer")
    val getPlayerNameMethod by +findBridgeMethod("bridge\$getName") { (owner) -> owner.methods.any { it.name == "bridge\$getGameProfile" } }
    val getUUIDMethod by +findBridgeMethod("bridge\$getUniqueID")
    val isWindowFocusedMethod by +findBridgeMethod("bridge\$isWindowFocused")
    val getServerDataMethod by +findBridgeMethod("bridge\$getCurrentServerData")
    val getServerIPMethod by +findBridgeMethod("bridge\$serverIP")
    val displayMessageMethod by +findBridgeMethod("bridge\$addChatMessage", { (owner) -> playerEntityBridge = owner })
    val renderPlayerItemsMethod by +FindMethod {
        it.method.name == "renderPlayerItems" &&
                it.owner.name.let { name -> name.startsWith("net/optifine/") && name.endsWith("PlayerConfigurations") }
    }

    val getVersionMethod by +FindMethod { it.method.calls(matchName("getMinecraftVersion")) }
    private val isVersionOldMethod by +FindMethod {
        it.owner.hasConstant("https://launchermeta.mojang.com/mc/game/version_manifest.json")
                && Type.getReturnType(it.method.desc) == Type.BOOLEAN_TYPE
                && it.method.hasConstant("v1_8")
    }

    val reloadCapeMethod by +FindMethod { it.method.name == "reloadCape" }
    val getDetails by +FindMethod {
        featureDetailsClass != null &&
                Type.getReturnType(it.method.desc).internalName == featureDetailsClass
    }

    // Method descriptions that have been found
    lateinit var getDisplayToIPMapMethod: MethodDescription
    lateinit var getServerMappingsMethod: MethodDescription
    lateinit var getLunarmainMethod: MethodDescription
    lateinit var getClientBridgeMethod: MethodDescription
    lateinit var toBridgeComponentMethod: MethodDescription
    lateinit var renderLayerMethod: MethodDescription
    lateinit var shouldRenderLayerMethod: MethodDescription

    // Field descriptions that have been found
    lateinit var assetsSocketField: FieldDescription
    lateinit var positionField: FieldDescription

    val versionIdField
        get() = isVersionOldMethod?.method?.instructions
            ?.filterIsInstance<FieldInsnNode>()
            ?.first()?.asDescription()

    // Available runtime data
    val version by runtimeValue("version", "unknown")
    val os by runtimeValue("os", "unknown")
    val arch by runtimeValue("arch", "unknown")

    // Finders that do not need to be stored
    init {
        +FindMethod({
            outgoingChatEvent = it.method.instructions.filterIsInstance<MethodInsnNode>()
                .last { c -> c.name == "getMessage" }
                .owner.replace('/', '.')
        }) {
            it.method.calls { m -> m.name == "getMessage" && m.owner.startsWith("lunar/") }
                    && it.owner.name.startsWith("net/minecraft/")
        }

        +FindMethod({
            getClientBridgeMethod = it.method.calls.first().asDescription()
        }) { it.method.calls(matchName("getLunarServer")) && it.owner.calls(matchName("bridge\$getPlayer")) }

        +FindMethod({ (_, method) ->
            toBridgeComponentMethod = method.calls
                .find {
                    Type.getArgumentTypes(it.desc)
                        .firstOrNull()?.internalName == "net/kyori/adventure/text/Component" &&
                            Type.getReturnType(it.desc).internalName.startsWith("lunar/")
                }!!.asDescription()
        }) { it.method.hasConstant(" [x\u0001]") }

        val renderMethodFilter: (MethodNode) -> Boolean = { it.desc.endsWith("FFFFFFF)V") }
        val shouldRenderMethodFilter: (MethodNode) -> Boolean = { it.desc == "()Z" }
        +FindClass({
            renderLayerMethod = it.methods.first(renderMethodFilter).asDescription(it)
            shouldRenderLayerMethod = it.methods.first(shouldRenderMethodFilter).asDescription(it)
            skinLayerClass = it.name
        }) {
            it.name.startsWith("lunar/") && it.isInterface && it.methods.any(renderMethodFilter) &&
                    it.methods.any(shouldRenderMethodFilter)
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

        +FindClass({
            positionField = it.fields.first { f -> Modifier.isStatic(f.access) }.asDescription(it)
        }) { it.hasConstant("top_left") }

        +FindClass({ featureDetailsClass = it.name }) { it.hasConstant("features.\u0001.details") }
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

        // Debug log information
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
    callBridgeMethod(RuntimeData.getServerDataMethod)
}

// Utility to load server mappings class onto the stack
fun MethodVisitor.getServerMappings() {
    getLunarMain()
    invokeMethod(InvocationType.VIRTUAL, RuntimeData.getServerMappingsMethod)
}

// Utility to load the boolean if the client window is focused onto the stack
fun MethodVisitor.isWindowFocused() {
    getClientBridge()
    callBridgeMethod(RuntimeData.isWindowFocusedMethod)
}

// Utility to convert a kyori adventure component that is currently on the stack
// to a lunar bridge component
fun MethodVisitor.toBridgeComponent() =
    invokeMethod(InvocationType.STATIC, RuntimeData.toBridgeComponentMethod)

// Utility to load the current player bridge
fun MethodVisitor.getPlayerBridge() {
    getClientBridge()
    callBridgeMethod(RuntimeData.getPlayerMethod)
}

// Utility to call a bridge method from a visitor
fun MethodVisitor.callBridgeMethod(methodInfo: RuntimeData.MethodInfo?) =
    invokeMethod(InvocationType.INTERFACE, methodInfo?.asDescription() ?: error("Couldn't find bridge method!"))