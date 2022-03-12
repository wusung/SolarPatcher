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
import com.grappenmaker.solarpatcher.asm.util.*
import com.grappenmaker.solarpatcher.config.Constants
import com.grappenmaker.solarpatcher.modules.*
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter

const val serializerName = "net/kyori/adventure/text/serializer/gson/GsonComponentSerializer"
const val componentName = "net/kyori/adventure/text/Component"

// Utility for generated classes on runtime.
// Used now solely for displaying messages in the minecraft chat.
// Can be used later for accessing Lunar Client internals with simple bytecode instructions.
object GeneratedCode {
    private const val prefix = "com/grappenmaker/solarpatcher/generated"
    private const val utilityClassName = "$prefix/Utility"
    private const val playerItemsLayerClassName = "$prefix/PlayerItemsLayer"
    private const val configDelegateClassName = "$prefix/ConfigDelegate"

    private val loader = object : ClassLoader(LunarClassLoader.loader!!) {
        fun createClass(name: String, file: ByteArray): Class<*> =
            defineClass(name, file, 0, file.size).also { resolveClass(it) }
    }

    private val utilityClass: Class<*> by lazy {
        generateClass(utilityClassName, interfaces = arrayOf(getInternalName<IGenerated>())) {
            with(
                visitMethod(
                    ACC_PUBLIC,
                    "displayMessage",
                    "(L${getInternalName<String>()};)V",
                    null,
                    null
                )
            ) { implementDisplayMessage() }
        }
    }

    private val playerItemsLayer: Class<*> by lazy {
        generateClass(
            playerItemsLayerClassName,
            interfaces = arrayOf(RuntimeData.skinLayerClass)
        ) {
            val (renderName, renderDesc) = RuntimeData.renderLayerMethod
            val (shouldRenderName) = RuntimeData.shouldRenderLayerMethod

            with(visitMethod(ACC_PUBLIC, renderName, renderDesc, null, null)) { implementRender() }
            with(visitMethod(ACC_PUBLIC, shouldRenderName, "()Z", null, null)) {
                visitCode()
                visitInsn(ICONST_1)
                returnMethod(IRETURN)
                visitMaxs(1, 0)
                visitEnd()
            }
        }
    }

    private val configFetcherDelegate: Class<*> by lazy {
        generateClass(configDelegateClassName, interfaces = arrayOf(getInternalName<IConfigDelegate>())) {
            fun MethodVisitor.getName() {
                loadVariable(1)
                visitTypeInsn(CHECKCAST, RuntimeData.playerEntityBridge.name)
                invokeMethod(
                    InvocationType.INTERFACE,
                    RuntimeData.getPlayerNameMethod?.asDescription() ?: error("No getName method found")
                )
            }

            with(visitMethod(ACC_PUBLIC, "getPlayerConfig", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null)) {
                // Delegate the call to a specialized class, no way I will implement this in bytecode
                visitCode()
                getObject(ConfigFetcher::class)
                getName()
                invokeMethod(ConfigFetcher::getConfig)
                returnMethod(ARETURN)
                visitMaxs(-1, -1)
                visitEnd()
            }

            with(visitMethod(ACC_PUBLIC, "setPlayerConfig", "(Ljava/lang/String;Ljava/lang/Object;)V", null, null)) {
                visitCode()
                getObject(ConfigFetcher::class)
                loadVariable(1)
                loadVariable(2)
                invokeMethod(ConfigFetcher::setConfig)
                returnMethod(RETURN)
                visitMaxs(-1, -1)
                visitEnd()
            }

            with(visitMethod(ACC_PUBLIC, "downloadPlayerConfig", "(L$internalString;)Ljava/lang/Object;", null, null)) {
                visitCode()

                construct(Constants.playerConfigurationName, "()V")
                construct(
                    Constants.fileDownloadThreadName,
                    "(Ljava/lang/String;Lnet/optifine/http/IFileDownloadListener;)V"
                ) {
                    invokeMethod(
                        InvocationType.STATIC,
                        "getPlayerItemsUrl",
                        "()L$internalString;",
                        Constants.httpUtilName
                    )
                    loadVariable(1)
                    concat("(L$internalString;L$internalString;)L$internalString;", "\u0001/users/\u0001.cfg")
                    construct(Constants.playerConfigReceivername, "(L$internalString;)V") { loadVariable(1) }
                }

                invokeMethod(Thread::start)
                returnMethod(ARETURN)

                visitMaxs(-1, -1)
                visitEnd()
            }
        }
    }

    val instance by lazy { utilityClass.getConstructor().newInstance() as IGenerated }
    val itemLayerInstance: Any by lazy { playerItemsLayer.getConstructor().newInstance() }
    val configFetcherInstance by lazy { configFetcherDelegate.getConstructor().newInstance() as IConfigDelegate }

    private inline fun generateClass(
        name: String,
        extends: String = "java/lang/Object",
        interfaces: Array<String> = arrayOf(),
        generator: ClassVisitor.() -> Unit
    ): Class<*> {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val visitor = TraceClassVisitor(writer, PrintWriter(System.out))
        visitor.visit(V9, ACC_PUBLIC, name, null, extends, interfaces)
        visitor.generator()

        with(visitor.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)) {
            visitCode()
            loadVariable(0)
            visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            returnMethod()
            visitMaxs(1, 0)
            visitEnd()
        }

        visitor.visitEnd()
        return loader.createClass(name.replace('/', '.'), writer.toByteArray())
    }
}

object GeneratedAccessor : IGenerated by GeneratedCode.instance

interface IGenerated {
    fun displayMessage(message: String)
}

object ConfigDelegateAccessor : IConfigDelegate by GeneratedCode.configFetcherInstance

interface IConfigDelegate {
    fun getPlayerConfig(player: Any): Any?
    fun setPlayerConfig(name: String, config: Any)
    fun downloadPlayerConfig(name: String): Any
}

private fun MethodVisitor.implementDisplayMessage() {
    visitCode()

    getPlayerBridge()

    visitMethodInsn(INVOKESTATIC, serializerName, "gson", "()L$serializerName;", true)
    loadVariable(1)
    invokeMethod(InvocationType.INTERFACE, "deserialize", "(Ljava/lang/Object;)L$componentName;", serializerName)
    toBridgeComponent()

    val displayMessageMethod = RuntimeData.displayMessageMethod ?: error("No display message method was found")
    invokeMethod(InvocationType.INTERFACE, displayMessageMethod.asDescription())

    returnMethod()
    visitMaxs(-1, -1)
    visitEnd()
}