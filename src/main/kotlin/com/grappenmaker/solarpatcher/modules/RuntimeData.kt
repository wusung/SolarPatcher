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

import com.grappenmaker.solarpatcher.asm.FieldDescription
import com.grappenmaker.solarpatcher.asm.asDescription
import com.grappenmaker.solarpatcher.asm.calls
import com.grappenmaker.solarpatcher.asm.constants
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.match
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchName
import com.grappenmaker.solarpatcher.asm.method.InvocationType
import com.grappenmaker.solarpatcher.asm.method.MethodDescription
import com.grappenmaker.solarpatcher.asm.method.asDescription
import com.grappenmaker.solarpatcher.asm.transform.ClassTransform
import com.grappenmaker.solarpatcher.asm.transform.VisitorTransform
import com.grappenmaker.solarpatcher.asm.util.*
import com.grappenmaker.solarpatcher.config.Constants.API
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ARRAYLENGTH
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import java.lang.reflect.Modifier
import kotlin.reflect.jvm.javaMethod

// Utility "module" to get runtime data
object RuntimeData : Module() {
    override val isEnabled = true
    private val knownData = mutableMapOf<String, Any?>()

    lateinit var lunarClientClass: String
    lateinit var getLunarmainMethod: MethodDescription
    var assetsSocketField: FieldDescription? = null
    lateinit var sendPopupMethod: MethodDescription

    var outgoingPacketEvent: String = "" // Not lateinit because that could crash the client
        private set

    val version by runtimeValue("version", "unknown")
    val os by runtimeValue("os", "unknown")
    val arch by runtimeValue("arch", "unknown")

    override fun generate(node: ClassNode): ClassTransform? {
        if (node.constants.contains("wss://assetserver.\u0001/connect")) handleAssetsSocket(node)
        if (node.constants.contains("Starting Lunar client...")) handleLunarMain(node)
        if (node.constants.containsAll(listOf("/lc_upload_screenshot", "Screenshot taken"))) handleScreenshotter(node)

        return null // Don't perform transformation
    }

    // Used for retrieving information about the current runtime
    private fun handleLunarMain(node: ClassNode) {
        println("Loading runtime values")

        try {
            lunarClientClass = node.name
            getLunarmainMethod =
                node.methods.find {
                    Type.getReturnType(it.desc).internalName == node.name
                            && Modifier.isStatic(it.access)
                }?.asDescription(node) ?: error("No get lunar main method?")

            val clinit = node.methods.find { MethodMatching.matchClinit().match(it.asDescription(node)) } ?: return
            val assignments = clinit.instructions
                .filterIsInstance<FieldInsnNode>()
                .filter { it.opcode == Opcodes.PUTSTATIC }
                .associate { it.name to (it.previous as? LdcInsnNode)?.cst }

            knownData += node.fields
                .associate { it.name to (it.value ?: assignments[it.name]) }
        } catch (e: Exception) {
            e.printStackTrace()
            println("Couldn't load runtime values!")
        }

        // Debuy log information
        println("Runtime information: ${knownData.toList()}")

        // Get assets socket field
        val call = node.methods.filter { it.constants.contains("Connected to the AssetServer") }
            .mapNotNull { it.calls.find { c -> c.name == "connect" } }.first()

        val insn = call.previous as FieldInsnNode
        assetsSocketField = FieldDescription(insn.name, insn.desc, insn.owner)
    }

    // Used for retrieving information on a specific event
    private fun handleScreenshotter(node: ClassNode) {
        val ctor = node.methods.find { it.name == "<init>" } ?: return
        ctor.instructions
            .filterIsInstance<LdcInsnNode>()
            .lastOrNull { it.cst is Type }
            ?.let { outgoingPacketEvent = (it.cst as Type).className }
    }

    // Used for getting the method with the popup call
    private fun handleAssetsSocket(node: ClassNode) {
        val method =
            node.methods.find { it.constants.contains("\u0001[\u0001\u0001\u0001] \u0001\u0001") } ?: return

        sendPopupMethod = method.asDescription(node)
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
    getField(RuntimeData.assetsSocketField!!, static = false)
}

// Utility "Module" to alter the lunar class loader to cache classes
object ClassCacher : Module() {
    val classCache = mutableMapOf<String, ByteArray>()

    override val isEnabled = true
    override fun generate(node: ClassNode): ClassTransform? {
        if (node.name != "WWWWWNNNNWMWNNNNWNNNNMMWW") return null
        return ClassTransform(VisitorTransform(matchName("findClass")) { parent ->
            object : MethodVisitor(API, parent) {
                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    name: String,
                    descriptor: String,
                    isInterface: Boolean
                ) {
                    if (name == "defineClass") {
                        pop(2) // Useless
                        storeVariable(7) // Keep class bytes
                        storeVariable(8) // Keep class name
                        pop() // Pop "this"

                        // Load correct parameters
                        loadVariable(8) // name
                        loadVariable(7) // bytes

                        // Call cacheClass
                        val desc = ClassCacher::cacheClass.javaMethod!!.asDescription()
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, desc.owner, desc.name, desc.descriptor, false)

                        // Reload all the other stuff
                        loadVariable(0) // this
                        loadVariable(8) // name
                        loadVariable(7) // bytes
                        visitInsn(ICONST_0)
                        loadVariable(7)
                        visitInsn(ARRAYLENGTH) // length

                        // Recall original method (drops out of if statement)
                    }

                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }
            }
        })
    }

    @JvmStatic
    fun cacheClass(name: String, bytes: ByteArray) {
        if (name.startsWith("net.minecraft.v")) {
            classCache[name.replace('.', '/')] = bytes
        }
    }
}