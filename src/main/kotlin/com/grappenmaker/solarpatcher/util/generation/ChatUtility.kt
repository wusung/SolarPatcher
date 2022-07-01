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

import com.grappenmaker.solarpatcher.asm.method.InvocationType
import com.grappenmaker.solarpatcher.asm.util.*
import com.grappenmaker.solarpatcher.modules.*
import org.objectweb.asm.Opcodes

private const val serializerName = "net/kyori/adventure/text/serializer/gson/GsonComponentSerializer"
private const val componentName = "net/kyori/adventure/text/Component"

val chatUtilityClass by lazy {
    val className = "${GeneratedCode.prefix}/ChatUtility"
    generateClass(className, interfaces = arrayOf(getInternalName<IChatUtility>())) {
        with(
            visitMethod(
                Opcodes.ACC_PUBLIC,
                "displayComponent",
                "(L${getInternalName<String>()};)V",
                null,
                null
            )
        ) {
            visitCode()

            getPlayerBridge()

            visitMethodInsn(Opcodes.INVOKESTATIC, serializerName, "gson", "()L$serializerName;", true)
            loadVariable(1)
            invokeMethod(
                InvocationType.INTERFACE,
                "deserialize",
                "(Ljava/lang/Object;)L$componentName;",
                serializerName
            )
            toBridgeComponent()
            callBridgeMethod(Bridge.displayMessageMethod)

            returnMethod()
            visitMaxs(-1, -1)
            visitEnd()
        }

        with(
            visitMethod(
                Opcodes.ACC_PUBLIC,
                "displayText",
                "(L${getInternalName<String>()};)V",
                null,
                null
            )
        ) {
            visitCode()

            loadVariable(0)
            loadVariable(1)
            concat("(Ljava/lang/String;)Ljava/lang/String;", "{\"text\": \"\u0001\"}")
            invokeMethod(
                InvocationType.VIRTUAL,
                "displayComponent",
                "(Ljava/lang/String;)V",
                className
            )

            returnMethod()
            visitMaxs(-1, -1)
            visitEnd()
        }
    }
}

interface IChatUtility {
    fun displayComponent(component: String)
    fun displayText(text: String)
}