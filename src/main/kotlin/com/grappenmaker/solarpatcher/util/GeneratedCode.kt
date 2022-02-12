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

import com.grappenmaker.solarpatcher.asm.method.MethodDescription
import com.grappenmaker.solarpatcher.asm.util.getInternalName
import com.grappenmaker.solarpatcher.asm.util.loadVariable
import com.grappenmaker.solarpatcher.asm.util.returnMethod
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.chat.ComponentSerializer
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*

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

        object : ClassLoader(LunarClassLoader.loader) {
            fun createClass(name: String, file: ByteArray): Class<*> =
                defineClass(name, file, 0, file.size).also { resolveClass(it) }
        }.createClass(utilityClassName, writer.toByteArray())
    }

    // Generated with Recaf
    private fun implementSendMessage(mv: MethodVisitor) {
        mv.visitParameter(null, 0)
        mv.visitFieldInsn(
            GETSTATIC,
            "net/optifine/Config",
            "minecraft",
            "Lnet/minecraft/v1_8/ahahseaaaseehhpahhhepppes;"
        )
        mv.visitFieldInsn(
            GETFIELD,
            "net/minecraft/v1_8/ahahseaaaseehhpahhhepppes",
            "phaaasapeahphshpsseesaees",
            "Lnet/minecraft/v1_8/spahhphhhpepeehphasehsppa;"
        )
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "net/minecraft/v1_8/spahhphhhpepeehphasehsppa",
            "aaahespaphhaeehehssehhape",
            "()Lnet/minecraft/v1_8/sapeaaeaeppaeehheahpphhpe;",
            false
        )

        mv.loadVariable(0)
        mv.visitMethodInsn(
            INVOKESTATIC,
            "net/minecraft/v1_8/hassheaeeeasssappsesaeppe\$espsppsaeashaehpesespeesa",
            "sshphhaeesehshphaahsheahh",
            "(Ljava/lang/String;)Lnet/minecraft/v1_8/hassheaeeeasssappsesaeppe;",
            false
        )

        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "net/minecraft/v1_8/sapeaaeaeppaeehheahpphhpe",
            "eaepeeaphhhaahspphespaehh",
            "(Lnet/minecraft/v1_8/hassheaeeeasssappsesaeppe;)V",
            false
        )
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

    @JvmStatic // See other displayMessage function
    fun displayMessage(component: BaseComponent) =
        displayMessage(ComponentSerializer.toString(component))
}