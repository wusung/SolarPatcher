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
import com.grappenmaker.solarpatcher.asm.hasConstant
import com.grappenmaker.solarpatcher.asm.matching.asMatcher
import com.grappenmaker.solarpatcher.asm.transform.ClassTransform
import com.grappenmaker.solarpatcher.asm.transform.VisitorTransform
import com.grappenmaker.solarpatcher.asm.util.createAdvice
import com.grappenmaker.solarpatcher.asm.util.invokeMethod
import com.grappenmaker.solarpatcher.config.json
import com.grappenmaker.solarpatcher.util.generation.Accessors
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.objectweb.asm.tree.ClassNode
import java.net.HttpURLConnection
import java.net.URL

// Launch requests
object LaunchRequestModule : Module() {
    override val isEnabled = true
    override fun generate(node: ClassNode): ClassTransform? {
        val method = node.methods.find { it.hasConstant("Starting Lunar client...") } ?: return null
        val desc = method.asDescription(node)
        return ClassTransform(listOf(VisitorTransform(desc.asMatcher()) { parent ->
            createAdvice(desc, parent, exitAdvice = {
                invokeMethod(::sendLaunch)
            })
        }), shouldExpand = true)
    }
}

// Don't worry; this is just a nice statistic for us :nerd:
fun sendLaunch() {
    runCatching {
        val actualType = when (val type = System.getProperty("solar.launchType")) {
            null -> "patcher"
            "shortcut" -> "launcher"
            "launcher" -> return println("Detected usage of the Solar Tweaks launcher")
            else -> return println("Invalid launch type $type, ignoring...")
        }

        val version = Accessors.Utility.getVersion().drop(1).replace('_', '.')
        with(URL("https://server.solartweaks.com/api/launch").openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            doOutput = true
            outputStream.bufferedWriter().write(json.encodeToString(LaunchRequest(actualType, version)))
        }
    }
        .onSuccess { println("Sent launch request") }
        .onFailure { println("Couldn't send launch request: $it") }
}

@Serializable
private data class LaunchRequest(val item: String, val version: String)