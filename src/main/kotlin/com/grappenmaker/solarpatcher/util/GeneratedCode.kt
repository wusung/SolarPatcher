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

package com.grappenmaker.solarpatcher.util

import com.grappenmaker.solarpatcher.asm.asDescription
import com.grappenmaker.solarpatcher.asm.method.InvocationType
import com.grappenmaker.solarpatcher.asm.method.MethodDescription
import com.grappenmaker.solarpatcher.asm.util.*
import com.grappenmaker.solarpatcher.modules.ClassCacher
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

// Utility for generated classes on runtime.
object GeneratedCode {
    private const val utilityClassName = "com.grappenmaker.solarpatcher.generated.Utility"
    private val displayMessageDescription = MethodDescription(
        "displayMessage",
        "(L${getInternalName<String>()};)V",
        utilityClassName.replace('.', '/'),
        ACC_PUBLIC or ACC_STATIC
    )

    private val utilityClass: Class<*> by lazy {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(V9, ACC_PUBLIC, utilityClassName.replace('.', '/'), null, "java/lang/Object", null)

        val (name, descriptor, _, access) = displayMessageDescription
        writer.visitMethod(access, name, descriptor, null, null).also(::implementSendMessage)
        writer.visitEnd()

        object : ClassLoader(LunarClassLoader.loader!!) {
            fun createClass(name: String, file: ByteArray): Class<*> =
                defineClass(name, file, 0, file.size).also { resolveClass(it) }
        }.createClass(utilityClassName, writer.toByteArray())
    }

    // Internal lunar thing
    private val patchHandler: Any by lazy {
        val loader = LunarClassLoader.loader!!
        val field = loader::class.java.getDeclaredField("WWWWWNNNNWMWWWMWWMMMWMMWM")
            .also { it.isAccessible = true }
        field[loader]
    }

    // Utility to get the class name map inside of lunar
    private val classnameMap: Map<String, String> by lazy {
        val field = patchHandler::class.java.getDeclaredField("MWMMWMMWNNNNMWMWNNNNWMWMM")
            .also { it.isAccessible = true }

        val map = field[patchHandler] as Map<*, *>

        // Be aware: it inverts!
        map.map { (key, value) -> value as String to key as String }.toMap()
    }

    private fun implementSendMessage(mv: MethodVisitor) {
        val minecraftMain = getNode("ave")
        val unknownManager = getNode("avo")
        val chatHandler = getNode("avt")

        val managerField = minecraftMain.fields.find { it.desc.contains(unknownManager.name) } ?: fail()
        val chatHandlerMethod =
            unknownManager.methods.find { Type.getReturnType(it.desc).internalName == chatHandler.name }
                ?: fail()

        mv.visitParameter(null, 0)
        mv.visitFieldInsn(
            GETSTATIC,
            "net/optifine/Config",
            "minecraft",
            "L${minecraftMain.name};"
        )
        mv.getField(managerField.asDescription(minecraftMain), static = false)
        mv.invokeMethod(InvocationType.VIRTUAL, chatHandlerMethod.asDescription(unknownManager))

        mv.loadVariable(0)

        val serializerNode = getNode("eu\$a")
        val chatComponentName = remapName("eu") ?: fail()
        val deserializeMethod = serializerNode.methods.find {
            it.access and ACC_STATIC != 0
                    && Type.getReturnType(it.desc).internalName == chatComponentName
        } ?: fail()
        mv.invokeMethod(InvocationType.STATIC, deserializeMethod.asDescription(serializerNode))

        val displayMessageMethod =
            chatHandler.methods.find { Type.getArgumentTypes(it.desc).firstOrNull()?.internalName == chatComponentName }
                ?: fail()
        mv.invokeMethod(InvocationType.VIRTUAL, displayMessageMethod.asDescription(chatHandler))

        mv.returnMethod()
        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }

    // Utility function to display a message in chat
    @JvmStatic // JvmStatic for use in bytecode
    fun displayMessage(message: String) {
        try {
            utilityClass.getMethod(displayMessageDescription.name, String::class.java)(null, message)
        } catch (e: ReflectiveOperationException) {
            e.printStackTrace()
            println("Error while sending chat message: $e")
        }
    }

    // Internal utility to remap nms classes names
    private fun remapName(className: String) =
        classnameMap[className.replace('/', '.')]?.replace('.', '/')

    // Cache
    private val nodesCache = mutableMapOf<String, ClassNode>()

    // Gets the classnode of a patched class
    private fun getNode(className: String) = nodesCache.getOrPut(className) {
        val classFile = ClassCacher.classCache[remapName(className) ?: className] ?: fail()
        ClassNode().also { ClassReader(classFile).accept(it, ClassReader.SKIP_DEBUG) }
    }
}

// Internal utility to fail generating code
// Use only when something nullable is very unlikely to be null
private fun fail(message: String = "Failed to generate code"): Nothing =
    throw IllegalStateException(message)