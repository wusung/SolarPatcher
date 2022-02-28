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

import com.grappenmaker.solarpatcher.asm.method.InvocationType
import com.grappenmaker.solarpatcher.asm.method.MethodDescription
import com.grappenmaker.solarpatcher.asm.util.getInternalName
import com.grappenmaker.solarpatcher.asm.util.invokeMethod
import com.grappenmaker.solarpatcher.asm.util.loadVariable
import com.grappenmaker.solarpatcher.asm.util.returnMethod
import com.grappenmaker.solarpatcher.modules.RuntimeData
import com.grappenmaker.solarpatcher.modules.getPlayerBridge
import com.grappenmaker.solarpatcher.modules.toBridgeComponent
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter

const val serializerName = "net/kyori/adventure/text/serializer/gson/GsonComponentSerializer"
const val componentName = "net/kyori/adventure/text/Component"

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
        val visitor = TraceClassVisitor(writer, PrintWriter(System.out))
        visitor.visit(V9, ACC_PUBLIC, utilityClassName.replace('.', '/'), null, "java/lang/Object", null)

        val (name, descriptor, _, access) = displayMessageDescription
        with(visitor.visitMethod(access, name, descriptor, null, null)) { implementDisplayMessage() }
        visitor.visitEnd()

        object : ClassLoader(LunarClassLoader.loader!!) {
            fun createClass(name: String, file: ByteArray): Class<*> =
                defineClass(name, file, 0, file.size).also { resolveClass(it) }
        }.createClass(utilityClassName, writer.toByteArray())
    }

    private fun MethodVisitor.implementDisplayMessage() {
        visitCode()

        getPlayerBridge()

        visitMethodInsn(INVOKESTATIC, serializerName, "gson", "()L$serializerName;", true)
        loadVariable(0)
        invokeMethod(InvocationType.INTERFACE, "deserialize", "(Ljava/lang/Object;)L$componentName;", serializerName)
        toBridgeComponent()

        invokeMethod(InvocationType.INTERFACE, RuntimeData.displayMessageMethod)

        returnMethod()
        visitMaxs(-1, -1)
        visitEnd()
    }

    // Utility function to display a message in chat
    @JvmStatic // JvmStatic for use in bytecode
    fun displayMessage(message: String) {
        try {
            utilityClass.getMethod(displayMessageDescription.name, String::class.java)(null, message)
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error while sending chat message: $e")
        }
    }
}