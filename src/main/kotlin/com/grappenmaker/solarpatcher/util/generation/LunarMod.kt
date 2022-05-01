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

package com.grappenmaker.solarpatcher.util.generation

import com.grappenmaker.solarpatcher.asm.util.*
import com.grappenmaker.solarpatcher.modules.*
import com.grappenmaker.solarpatcher.util.ensure
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import java.util.*

private var id: Int = 0
    get() = field++

fun createLunarMod(name: String, previewText: String = name, provider: MethodVisitor.() -> Unit): Any {
    val textModClass = ModRuntime.textModClass.ensure()
    val clazz = generateClass(
        "${GeneratedCode.prefix}/LunarMod$id",
        extends = textModClass.name,
        createConstructor = false
    ) {
        with(visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)) {
            visitCode()

            val ctor = textModClass.methods.find { it.name == "<init>" } ?: error("No ctor?")
            loadVariable(0)
            visitInsn(ICONST_0)
            getField(ModRuntime.positionField)
            visitMethodInsn(INVOKESPECIAL, textModClass.name, "<init>", ctor.desc, false)
            returnMethod()

            visitMaxs(-1, -1)
            visitEnd()
        }

        // Iterate over all the methods we need to implement
        val (preview, preferred, textMethod) = textModClass.methods.filter { it.access == 0x401 }
        with(visitMethod(ACC_PUBLIC, preview.name, preview.desc, null, null)) {
            visitCode()
            visitLdcInsn(previewText)
            returnMethod(ARETURN)
            visitMaxs(-1, -1)
            visitEnd()
        }

        with(visitMethod(ACC_PUBLIC, preferred.name, preferred.desc, null, null)) {
            visitCode()

            val type = Type.getReturnType(preferred.desc).internalName
            construct(type, "(IIIIII)V") {
                listOf(10, 18, 22, 50, 56, 62).forEach { visitIntInsn(BIPUSH, it) }
            }
            returnMethod(ARETURN)

            visitMaxs(-1, -1)
            visitEnd()
        }

        with(visitMethod(ACC_PUBLIC, textMethod.name, textMethod.desc, null, null)) {
            visitCode()
            provider()
            returnMethod(ARETURN)
            visitMaxs(1, 0)
            visitEnd()
        }

        // Implement get feature details
        val (_, featureDetailsMethod) = ModRuntime.getDetails.ensure()
        with(visitMethod(ACC_PUBLIC, featureDetailsMethod.name, featureDetailsMethod.desc, null, null)) {
            visitCode()
            construct(Type.getReturnType(featureDetailsMethod.desc).internalName, "(L$internalString;)V") {
                visitLdcInsn(name.lowercase())
            }

            returnMethod(ARETURN)
            visitMaxs(-1, -1)
            visitEnd()
        }
    }

    return clazz.getConstructor().newInstance()
}

fun createAccountNameMod(name: String) = createLunarMod(name, "[Steve]") {
    getPlayerBridge()
    callBridgeMethod(Bridge.getPlayerNameMethod)
}

fun createTextMod(name: String, text: String) =
    createLunarMod(name, "[$text]") { visitLdcInsn(text) }