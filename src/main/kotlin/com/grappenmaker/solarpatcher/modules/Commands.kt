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

import com.grappenmaker.solarpatcher.util.GeneratedCode
import kotlinx.serialization.Serializable
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent

// Utility for command handling
class CommandEventAccessor(private val event: Any) {
    private val textField = event.javaClass.fields.first()
    private val cancelledField = event.javaClass.superclass.fields.first()

    var text: String
        get() = textField[event] as String
        set(value) {
            textField[event] = value
        }

    var cancelled: Boolean
        get() = cancelledField[event] as Boolean
        set(value) {
            cancelledField[event] = value
        }

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
        val component = TextComponent(helpMessage).also {
            it.isItalic = true
            it.clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, discordLink)
            it.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, arrayOf(TextComponent("Click here!")))
        }

        GeneratedCode.displayMessage(component)
    }

    val easterEgg = HandlerCommand {
        cancel()
        val magicComponent = TextComponent("a").also { it.isObfuscated = true }
        val component = TextComponent("").also {
            val actualText = TextComponent(" NotEvenJoking was here ").also { t -> t.isObfuscated = false }
            it.extra = listOf(magicComponent, actualText, magicComponent)
            it.color = ChatColor.DARK_PURPLE
        }

        GeneratedCode.displayMessage(component)
    }

    return listOf("solartweaks", "solarhelp", "solarsupport", "solartweakshelp", "solartweakssupport", "st")
        .associateWith { handlerCommand } + mapOf("whowashere" to easterEgg)
}