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

import com.grappenmaker.solarpatcher.Versioning
import com.grappenmaker.solarpatcher.configuration
import com.grappenmaker.solarpatcher.util.ConfigDelegateAccessor
import com.grappenmaker.solarpatcher.util.GeneratedAccessor
import com.grappenmaker.solarpatcher.util.javaReflectionProperty
import kotlinx.serialization.Serializable
import java.awt.Desktop
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    val reloadCosmetics = HandlerCommand {
        cancel()
        GeneratedAccessor.displayMessage("""{"text": "Reloading your cosmetics...", "color": "gray"}""")

        try {
            ConfigDelegateAccessor.reloadPlayerCosmetics()
            GeneratedAccessor.displayMessage("""{"text": "Successfully unregistered cosmetics!", "color": "green"}""")
        } catch (e: Exception) {
            GeneratedAccessor.displayMessage("""{"text": "There was an error while reloading your cosmetics:\n${e.message}", "color": "red"}""")
        }
    }

    val debugCommand = HandlerCommand {
        cancel()
        val formattedDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(
            Instant.ofEpochMilli(Versioning.buildTimestamp)
                .atZone(ZoneId.systemDefault()).toOffsetDateTime()
        )
        val moduleText = configuration.modules.map { m -> m::class.simpleName ?: "Unnamed" }
            .sorted().joinToString()

        GeneratedAccessor.displayMessage(
            """[
            "",
            {
                "text": "Solar Patcher Debug",
                "color": "red"
            },
            {
                "text": "\n\n"
            },
            {
                "text": "Minecraft Version: ",
                "color": "green"
            },
            {
                "text": "${GeneratedAccessor.getVersion()}\n"
            },
            {
                "text": "Patcher Version: ",
                "color": "green"
            },
            {
                "text": "${Versioning.version}\n"
            },
            {
                "text": "Built date: ",
                "color": "green"
            },
            {
                "text": "$formattedDate\n"
            },
            {
                "text": "Build type: ",
                "color": "green"
            },
            {
                "text": "${if (Versioning.devBuild) "Development" else "Production"}\n"
            },
            {
                "text": "Player: ",
                "color": "green"
            },
            {
                "text": "${GeneratedAccessor.getPlayerName()}\n"
            },
            {
                "text": "UUID: ",
                "color": "green"
            },
            {
                "text": "${GeneratedAccessor.getPlayerUUID()}\n"
            },
            {
                "text": "Current Server: ",
                "color": "green"
            },
            {
                "text": "${GeneratedAccessor.getServerIP() ?: "Singleplayer"}\n"
            },
            {
                "text": "Active modules (${configuration.modules.size}): ",
                "color": "green"
            },
            {
                "text": "$moduleText\n"
            },
            {
                "text": "Cosmetics Server: ",
                "color": "green"
            },
            {
                "text": "${configuration.optifineItems.capeServer}"
            }
        ]"""
        )
    }

    return listOf("solartweaks", "solarhelp", "solarsupport", "solartweakshelp", "solartweakssupport", "st")
        .associateWith { handlerCommand } + mapOf(
        "whowashere" to easterEgg,
        "rickroll" to rickroll,
        "reloadcosmetics" to reloadCosmetics,
        "solardebug" to debugCommand
    )
}