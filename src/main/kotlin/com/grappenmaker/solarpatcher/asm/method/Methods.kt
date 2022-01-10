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

package com.grappenmaker.solarpatcher.asm.method

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.objectweb.asm.Opcodes

// Description of a method (simple)
@Serializable
data class MethodDescription(
    val name: String,
    val descriptor: String,
    @Transient
    val owner: String = "", // empty string means -> don't check for owner
    @Transient
    val access: Int = -1 // -1 means -> don't check for access
) {
    fun match(other: MethodDescription) =
        other.name == name
                && other.descriptor == descriptor
                && (other.owner == owner || owner.isEmpty())
                && (other.access == access || access == -1)

    companion object {
        val CLINIT = MethodDescription("<clinit>", "()V")
    }
}

// Invocation types (see JVMS)
enum class InvocationType(val opcode: Int) {
    SPECIAL(Opcodes.INVOKESPECIAL),
    DYNAMIC(Opcodes.INVOKEDYNAMIC),
    VIRTUAL(Opcodes.INVOKEVIRTUAL),
    INTERFACE(Opcodes.INVOKEINTERFACE),
    STATIC(Opcodes.INVOKESTATIC);

    companion object {
        fun getFromOpcode(opcode: Int) = enumValues<InvocationType>().find { it.opcode == opcode }
    }
}