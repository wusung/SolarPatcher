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

import com.grappenmaker.solarpatcher.asm.asDescription
import com.grappenmaker.solarpatcher.asm.calls
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching
import com.grappenmaker.solarpatcher.asm.method.InvocationType
import com.grappenmaker.solarpatcher.asm.method.MethodDescription
import com.grappenmaker.solarpatcher.asm.util.invokeMethod
import com.grappenmaker.solarpatcher.asm.util.loadVariable
import com.grappenmaker.solarpatcher.asm.util.returnMethod
import com.grappenmaker.solarpatcher.modules.RuntimeData
import com.grappenmaker.solarpatcher.modules.shouldImplementItems
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type

internal val playerItemsLayerClass: Class<*> by lazy {
    generateClass("${GeneratedCode.prefix}/PlayerItemsLayer", interfaces = arrayOf(RuntimeData.skinLayerClass)) {
        val (renderName, renderDesc) = RuntimeData.renderLayerMethod
        val (shouldRenderName) = RuntimeData.shouldRenderLayerMethod

        with(visitMethod(ACC_PUBLIC, renderName, renderDesc, null, null)) {
            visitCode()
            // Argument 0: bridge
            // Argument 1: renderer
            // Argument 2: entity bridge
            // Argument 3: player model
            // 4 float limbSwing
            // 5 float limbSwingAmount
            // 6 float partialTicks
            // 7 float ticksExisted
            // 8 float headYaw
            // 9 float rotationPitch
            // 10 float scale

            val instanceof = Label()
            loadVariable(2)
            // Check cast for player entity bridge
            val playerBridgeName = RuntimeData.playerEntityBridge.name
            visitTypeInsn(INSTANCEOF, playerBridgeName)
            visitJumpInsn(IFEQ, instanceof)

            // Prepare render
            callRenderer("bridge\$color") { repeat(4) { visitInsn(FCONST_1) } }
            callRenderer("bridge\$disableRescaleNormal")

            // Call render method
            val methodInfo = RuntimeData.renderPlayerItemsMethod ?: error("No render player items method was found?")
            val arguments = Type.getArgumentTypes(methodInfo.method.desc)

            // Load arguments
            loadVariable(3)
            visitTypeInsn(CHECKCAST, arguments[0].internalName)
            loadVariable(2)
            visitTypeInsn(CHECKCAST, playerBridgeName)
            visitTypeInsn(CHECKCAST, arguments[1].internalName)
            loadVariable(10, FLOAD)
            loadVariable(6, FLOAD)
            invokeMethod(InvocationType.STATIC, methodInfo.asDescription())

            // Done!

            visitLabel(instanceof)
            returnMethod()
            visitMaxs(-1, -1)
            visitEnd()
        }

        with(visitMethod(ACC_PUBLIC, shouldRenderName, "()Z", null, null)) {
            visitCode()
            if (shouldImplementItems()) visitInsn(ICONST_1) else visitInsn(ICONST_0)
            returnMethod(IRETURN)
            visitMaxs(1, 0)
            visitEnd()
        }
    }
}

private inline fun MethodVisitor.callRenderer(name: String, arguments: MethodVisitor.() -> Unit = {}) {
    loadVariable(1)
    arguments()
    invokeMethod(InvocationType.VIRTUAL, getRendererMethod(name))
}

private fun getRendererMethod(name: String): MethodDescription {
    val renderer = RuntimeData.renderer ?: error("Renderer has not been found!")
    return renderer.methods.find { it.calls(MethodMatching.matchName(name)) }?.asDescription(renderer)
        ?: error("Requested render method does not exist!")
}