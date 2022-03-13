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

import com.grappenmaker.solarpatcher.asm.asDescription
import com.grappenmaker.solarpatcher.asm.calls
import com.grappenmaker.solarpatcher.asm.isInterface
import com.grappenmaker.solarpatcher.asm.matching.MethodMatching.matchName
import com.grappenmaker.solarpatcher.asm.matching.asMatcher
import com.grappenmaker.solarpatcher.asm.method.InvocationType
import com.grappenmaker.solarpatcher.asm.method.MethodDescription
import com.grappenmaker.solarpatcher.asm.transform.ClassTransform
import com.grappenmaker.solarpatcher.asm.transform.ConstantValueTransform
import com.grappenmaker.solarpatcher.asm.transform.ImplementTransform
import com.grappenmaker.solarpatcher.asm.transform.VisitorTransform
import com.grappenmaker.solarpatcher.asm.util.*
import com.grappenmaker.solarpatcher.config.Constants
import com.grappenmaker.solarpatcher.config.Constants.API
import com.grappenmaker.solarpatcher.util.ConfigDelegateAccessor
import com.grappenmaker.solarpatcher.util.GeneratedCode
import com.grappenmaker.solarpatcher.util.IConfigDelegate
import kotlinx.serialization.Serializable
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

@Serializable
data class OptifineItems(
    val capeServer: String = "http://capes.mantle.gg",
    override val isEnabled: Boolean = false
) : Module() {
    override fun generate(node: ClassNode) =
        handleAddLayer(node) ?: handleGetConfig(node) ?: handleHttpUtil(node)

    private fun handleHttpUtil(node: ClassNode): ClassTransform? = if (node.name == Constants.httpUtilName) {
        ClassTransform(ConstantValueTransform(matchName("getPlayerItemsUrl"), capeServer))
    } else null

    private fun handleGetConfig(node: ClassNode): ClassTransform? {
        val getConfigMethod = node.methods.find { it.name == "getPlayerConfiguration" } ?: return null
        if (getConfigMethod.instructions.size() > 2) return null
        return if (node.name == Constants.playerConfigurationsName) {
            ClassTransform(listOf(
                ImplementTransform(matchName("getPlayerConfiguration")) {
                    getObject(ConfigDelegateAccessor::class)
                    loadVariable(0)
                    invokeMethod(IConfigDelegate::getPlayerConfig)
                    visitTypeInsn(CHECKCAST, Constants.playerConfigurationName)
                    returnMethod(ARETURN)
                },
                ImplementTransform(matchName("setPlayerConfiguration")) {
                    getObject(ConfigDelegateAccessor::class)
                    loadVariable(0)
                    loadVariable(1)
                    invokeMethod(IConfigDelegate::setPlayerConfig)
                    returnMethod(RETURN)
                }
            ))
        } else null
    }

    private fun handleAddLayer(node: ClassNode): ClassTransform? {
        if (node.isInterface) return null

        val addLayerMethod = "bridge\$addLayer"
        val initMethod = node.methods.find { m -> m.calls.any { it.name == addLayerMethod } } ?: return null

        return ClassTransform(VisitorTransform(initMethod.asDescription(node).asMatcher()) { parent ->
            object : MethodVisitor(API, parent) {
                private var hasVisited = false

                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    name: String,
                    descriptor: String,
                    isInterface: Boolean
                ) {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

                    if (name == addLayerMethod && !hasVisited) {
                        val label = Label()
                        invokeMethod(::shouldImplementItems)
                        visitJumpInsn(IFEQ, label)

                        hasVisited = true
                        loadVariable(3)
                        getObject(GeneratedCode::class)
                        invokeMethod(GeneratedCode::itemLayerInstance.getter)
                        visitInsn(ICONST_1)
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)

                        visitLabel(label)
                    }
                }
            }
        })
    }
}

fun MethodVisitor.implementRender() {
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
    // Variable 11: casted player entity bridge

    val instanceof = Label()
    loadVariable(2)

    // Check cast for player entity bridge
    val playerBridgeName = RuntimeData.playerEntityBridge.name
    visitTypeInsn(INSTANCEOF, playerBridgeName)
    visitJumpInsn(IFEQ, instanceof)

    // Checked cast to player bridge
    loadVariable(2)
    visitTypeInsn(CHECKCAST, playerBridgeName)
    storeVariable(11)

    // Prepare render
    callRenderer("bridge\$color") { repeat(4) { visitInsn(FCONST_1) } }
    callRenderer("bridge\$disableRescaleNormal")
//    callRenderer("bridge\$enableCull")

    // Call render method
    val methodInfo = RuntimeData.renderPlayerItemsMethod ?: error("No render player items method was found?")
    val arguments = Type.getArgumentTypes(methodInfo.method.desc)

    // Load arguments
    loadVariable(3)
    visitTypeInsn(CHECKCAST, arguments[0].internalName)
    loadVariable(11)
    visitTypeInsn(CHECKCAST, arguments[1].internalName)
    loadVariable(10, FLOAD)
    loadVariable(6, FLOAD)
    invokeMethod(InvocationType.STATIC, methodInfo.asDescription())

    // Remove culling
//    callRenderer("bridge\$disableCull")

    // Done!

    visitLabel(instanceof)
    returnMethod()
    visitMaxs(-1, -1)
    visitEnd()
}

private inline fun MethodVisitor.callRenderer(name: String, arguments: MethodVisitor.() -> Unit = {}) {
    loadVariable(1)
    arguments()
    invokeMethod(InvocationType.VIRTUAL, getRendererMethod(name))
}

private fun getRendererMethod(name: String): MethodDescription {
    val renderer = RuntimeData.renderer ?: error("Renderer has not been found!")
    return renderer.methods.find { it.calls(matchName(name)) }?.asDescription(renderer)
        ?: error("Requested render method does not exist!")
}

object ConfigFetcher {
    val configs = mutableMapOf<String, Any>() // Player name to config
    fun getConfig(name: String?): Any? {
        if (name == null) return null
        if (!shouldImplementItems()) return null
        return configs.getOrPut(name) { ConfigDelegateAccessor.downloadPlayerConfig(name) }
    }

    fun setConfig(name: String?, value: Any?) {
        if (name == null || value == null) return
        configs[name] = value
    }
}

fun shouldImplementItems() = ConfigDelegateAccessor.getVersion() in listOf("v1_8", "v1_7")