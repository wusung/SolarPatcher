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

package com.grappenmaker.solarpatcher.asm.transform

import com.grappenmaker.solarpatcher.asm.method.*
import com.grappenmaker.solarpatcher.asm.util.ClassVisitorWrapper
import com.grappenmaker.solarpatcher.asm.util.MethodVisitorWrapper
import com.grappenmaker.solarpatcher.asm.util.invokeMethod
import com.grappenmaker.solarpatcher.asm.util.pop
import com.grappenmaker.solarpatcher.config.API
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.*
import java.lang.reflect.Method

// A visitor that applies method transforms
class TransformVisitor(private val transforms: List<MethodTransform>, parent: ClassVisitor) :
    ClassVisitor(API, parent) {
    private lateinit var owner: String

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        super.visit(version, access, name, signature, superName, interfaces)
        this.owner = name
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val parent = super.visitMethod(access, name, descriptor, signature, exceptions)
        return transforms.filter { it.desc.match(MethodDescription(name, descriptor, owner, access)) }
            .fold(parent) { acc, cur -> cur.asVisitor(acc) }
    }
}

// A class transformer
class ClassTransform(
    val className: String,
    val methodTransforms: List<MethodTransform> = listOf(),
    val visitors: List<ClassVisitorWrapper> = listOf(),
    val shouldExpand: Boolean = false
)

// A method transformer
abstract class MethodTransform(val desc: MethodDescription) {
    abstract fun asVisitor(parent: MethodVisitor): MethodVisitor
}

// Transformer to search-and-replace text in a given method
class TextTransform(
    description: MethodDescription,
    val from: String,
    val to: String
) : MethodTransform(description) {
    override fun asVisitor(parent: MethodVisitor) = object : MethodVisitor(API, parent) {
        override fun visitLdcInsn(value: Any?) =
            if (value is String) super.visitLdcInsn(value.replace(from, to)) else super.visitLdcInsn(value)

        override fun visitInvokeDynamicInsn(
            name: String,
            descriptor: String,
            handle: Handle,
            vararg args: Any
        ) {
            val newArgs = args.map { if (it is String) it.replace(from, to) else it }.toTypedArray()
            super.visitInvokeDynamicInsn(name, descriptor, handle, *newArgs)
        }
    }
}

// Transformer used to transform method invocations
abstract class InvokeTransform(
    desc: MethodDescription,
    val transformed: MethodDescription
) : MethodTransform(desc) {
    abstract val replacement: MethodVisitor.(opcode: Int, description: MethodDescription) -> Unit

    override fun asVisitor(parent: MethodVisitor) = object : MethodVisitor(API, parent) {
        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            // Using transformed.access here to avoid being checked on access
            val calledMethod = MethodDescription(name, descriptor, owner, transformed.access)
            if (transformed.match(calledMethod)) {
                replacement(parent, opcode, calledMethod)
                return
            }

            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }
    }
}

// Replaces the invocation of a method with a new method, with an optional
// "preparation" code block
class ReplaceInvokeTransform(
    desc: MethodDescription,
    invokedDescription: MethodDescription,
    newMethod: MethodDescription,
    invocationType: InvocationType,
    isInterface: Boolean = invocationType == InvocationType.INTERFACE,
    prepare: MethodVisitor.() -> Unit = {}
) : InvokeTransform(desc, invokedDescription) {
    override val replacement: MethodVisitor.(Int, MethodDescription) -> Unit = { _, _ ->
        prepare()

        val (name, descriptor, owner) = newMethod
        visitMethodInsn(invocationType.opcode, owner, name, descriptor, isInterface)
    }

    companion object {
        // Constructs a transformer that replaces the invocation of a method
        // with a given Method, with a preperation block
        fun fromMethod(
            desc: MethodDescription,
            invokedDescription: MethodDescription,
            method: Method,
            prepare: (MethodVisitor) -> Unit = {}
        ) = ReplaceInvokeTransform(
            desc,
            invokedDescription,
            method.asDescription(),
            method.invocationType,
            method.isStatic,
            prepare
        )
    }
}

// Transformer to replace a method with a code block
open class ReplaceCodeTransform(
    desc: MethodDescription,
    invokedDescription: MethodDescription,
    override val replacement: MethodVisitor.(Int, MethodDescription) -> Unit,
) : InvokeTransform(desc, invokedDescription)

// Transformer to remove a method call
class RemoveInvokeTransform(
    desc: MethodDescription,
    invokedDescription: MethodDescription,
    popCount: Int = 0
) : ReplaceCodeTransform(desc, invokedDescription, { _, _ -> if (popCount > 0) pop(popCount) })

// Transformer to add code before and/or after a method call
class InvokeAdviceTransform(
    desc: MethodDescription,
    invokedDescription: MethodDescription,
    val beforeAdvice: MethodVisitor.() -> Unit = {},
    val afterAdvice: MethodVisitor.() -> Unit = {}
) : InvokeTransform(desc, invokedDescription) {
    override val replacement: MethodVisitor.(Int, MethodDescription) -> Unit = { opcode, call ->
        beforeAdvice()
        invokeMethod(InvocationType.getFromOpcode(opcode)!!, call)
        afterAdvice()
    }
}

// Transformer to replace the implementation of a method
// WARNING: all other transforms are likely not to work with this transform,
// same for visitors. Use with cause!
// Parameters (and other meta) should also be declared manually (again)
open class ImplementTransform(
    desc: MethodDescription,
    val implementation: MethodVisitor.() -> Unit
) : MethodTransform(desc) {
    // Ignoring parent, won't delegate
    override fun asVisitor(parent: MethodVisitor) = object : MethodVisitor(API, null) {
        override fun visitCode() {
            parent.visitCode()
            parent.implementation()
            parent.visitMaxs(-1, -1)
            parent.visitEnd()
        }
    }
}

// Transfomer to replace the method with a stub method
class StubMethodTransform(desc: MethodDescription) : ImplementTransform(desc, {
    val returnType = getMethodType(desc.descriptor).returnType
    when (returnType.sort) {
        VOID -> visitInsn(RETURN)
        BOOLEAN, BYTE, SHORT, CHAR, INT -> {
            visitInsn(ICONST_0)
            visitInsn(IRETURN)
        }
        Type.FLOAT -> {
            visitInsn(ICONST_0)
            visitInsn(I2F)
            visitInsn(FRETURN)
        }
        Type.DOUBLE -> {
            visitInsn(ICONST_0)
            visitInsn(I2D)
            visitInsn(DRETURN)
        }
        Type.LONG -> {
            visitInsn(ICONST_0)
            visitInsn(I2L)
            visitInsn(LRETURN)
        }
        OBJECT, ARRAY -> {
            visitInsn(ACONST_NULL)
            visitInsn(ARETURN)
        }
    }
})

// Utility to wrap a methodvisitor into a transform
class VisitorTransform(
    desc: MethodDescription,
    val visitor: MethodVisitorWrapper
) : MethodTransform(desc) {
    override fun asVisitor(parent: MethodVisitor) = visitor(parent)
}
