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

import com.grappenmaker.solarpatcher.util.GeneratedAccessor
import com.grappenmaker.solarpatcher.util.javaReflectionProperty
import kotlinx.serialization.Serializable
import java.awt.Desktop
import java.net.URI

// Utility for command handling
class CommandEventAccessor(event: Any) {
    private val textField = event.javaClass.fields.first()
    private val cancelledField = event.javaClass.superclass.fields.first()

    var text: String by javaReflectionProperty(textField, event)
    var cancelled: Boolean by javaReflectionProperty(cancelledField, event)

    fun cancel() {
        cancelled = true
    }
}

sealed class Command {
    abstract fun handle(accessor: CommandEventAccessor)
}

@Serializable
data class TextCommand(val prefix: String, val suffix: String = "") : Command() {
    override fun handle(accessor: CommandEventAccessor) {
        val components = accessor.text.split(" ")
        accessor.text = prefix + components.drop(1).joinToString(" ") + suffix
    }
}

class HandlerCommand(val handler: CommandEventAccessor.() -> Unit) : Command() {
    override fun handle(accessor: CommandEventAccessor) = accessor.handler()
}

// Custom implemented commands
const val discordLink = "https://discord.solartweaks.com/"
const val helpMessage = "If you need help, or have a suggestion, you can always get support at $discordLink"

fun getCodeCommands(): Map<String, Command> {
    val handlerCommand = HandlerCommand {
        cancel()
        GeneratedAccessor.displayMessage(
            """
            {
                "italic": true,
                "clickEvent": {
                    "action": "open_url",
                    "value": "$discordLink"
                },
                "hoverEvent": {
                    "action": "show_text",
                    "contents": "Click here!"
                },
                "text": "$helpMessage"
            }            
        """
        )
    }

    val easterEgg = HandlerCommand {
        cancel()
        GeneratedAccessor.displayMessage(
            """
            {
                "color": "dark_purple",
                "extra": [
                    {
                        "obfuscated": true,
                        "text": "a"
                    },
                    {
                        "obfuscated": false,
                        "text": " NotEvenJoking was here "
                    },
                    {
                        "obfuscated": true,
                        "text": "a"
                    }
                ],
                "text": ""
            }
        """
        )
    }

    // :D
    val rickroll = HandlerCommand {
        cancel()
        Desktop.getDesktop().browse(URI("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    return listOf("solartweaks", "solarhelp", "solarsupport", "solartweakshelp", "solartweakssupport", "st")
        .associateWith { handlerCommand } + mapOf("whowashere" to easterEgg, "rickroll" to rickroll)
}