package com.grappenmaker.solarpatcher.asm

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