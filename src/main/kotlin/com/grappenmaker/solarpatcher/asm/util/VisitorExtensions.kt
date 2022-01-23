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

package com.grappenmaker.solarpatcher.asm.util

import com.grappenmaker.solarpatcher.asm.method.*
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import java.io.PrintStream
import java.lang.invoke.StringConcatFactory
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

val outField: Field by lazy { System::class.java.getField("out") }
val printlnMethod: Method by lazy { PrintStream::class.java.getMethod("println", String::class.java) }
val printMethod: Method by lazy { PrintStream::class.java.getMethod("print", String::class.java) }
val concatHandle: Handle by lazy {
    val method = StringConcatFactory::makeConcatWithConstants.javaMethod!!
    Handle(
        H_INVOKESTATIC,
        getInternalName<StringConcatFactory>(),
        method.name,
        Type.getMethodDescriptor(method),
        false
    )
}

// Makes a methodvisitor invoke a given method
// Caller is responsible for providing arguments and a target, if applicable
fun MethodVisitor.invokeMethod(method: Method) = visitMethodInsn(
    method.invocationType.opcode,
    method.declaringClass.internalName,
    method.name,
    Type.getMethodDescriptor(method),
    method.declaringClass.isInterface
)

fun <R> MethodVisitor.invokeMethod(func: KFunction<R>) =
    invokeMethod(func.javaMethod ?: error("Not a valid java method available"))

fun MethodVisitor.invokeMethod(invocationType: InvocationType, name: String, descriptor: String, owner: String) =
    visitMethodInsn(invocationType.opcode, owner, name, descriptor, invocationType == InvocationType.INTERFACE)

fun MethodVisitor.invokeMethod(invocationType: InvocationType, desc: MethodDescription) {
    val (name, descriptor, owner) = desc
    invokeMethod(invocationType, name, descriptor, owner)
}

// Makes a methodvisitor get the value of a field, and store it on the operand stack
// Caller is responsible for providing a target, if applicable
fun MethodVisitor.getField(field: Field) =
    visitFieldInsn(
        if (Modifier.isStatic(field.modifiers)) GETSTATIC else GETFIELD,
        field.declaringClass.internalName,
        field.name,
        Type.getDescriptor(field.type)
    )

// Makes a methodvisitor get set value of a field
// Caller is responsible for providing a value and target
fun MethodVisitor.setField(field: Field) =
    visitFieldInsn(
        if (Modifier.isStatic(field.modifiers)) PUTSTATIC else PUTFIELD,
        field.declaringClass.internalName,
        field.name,
        Type.getDescriptor(field.type)
    )

// Makes a methodvisitor construct a class with a given constructor.
// New instance will be pushed onto the stack
// Arguments for the constructor are initialized with the given block
inline fun MethodVisitor.construct(constructor: Constructor<*>, block: MethodVisitor.() -> Unit = {}) {
    visitTypeInsn(NEW, constructor.declaringClass.internalName)
    dup()
    block()
    visitMethodInsn(
        INVOKESPECIAL,
        constructor.declaringClass.internalName,
        "<init>",
        Type.getConstructorDescriptor(constructor),
        false
    )
}

inline fun MethodVisitor.construct(func: KFunction<Unit>, block: MethodVisitor.() -> Unit = {}) =
    construct(func.javaConstructor!!, block)

inline fun MethodVisitor.construct(className: String, descriptor: String, block: MethodVisitor.() -> Unit = {}) {
    visitTypeInsn(NEW, className)
    dup()
    block()
    visitMethodInsn(INVOKESPECIAL, className, "<init>", descriptor, false)
}

// Makes a methodvisitor get the value of the stdout stream
fun MethodVisitor.getOut() = getField(outField)

// Makes a methodvisitor print something (with newline)
// Block is used to initialize the value of the printed text
// Caller must make sure that the first value of the stack is a string
inline fun MethodVisitor.visitPrintln(block: MethodVisitor.() -> Unit) {
    getOut()
    block()
    invokeMethod(printlnMethod)
}

fun MethodVisitor.visitPrintln(string: String) = visitPrintln { visitLdcInsn(string) }

// Makes a methodvisitor print something
// Block is used to initialize the value of the printed text
// Caller must make sure that the first value of the stack is a string
inline fun MethodVisitor.visitPrint(block: MethodVisitor.() -> Unit) {
    getOut()
    block()
    invokeMethod(printMethod)
}

fun MethodVisitor.visitPrint(string: String) = visitPrint { visitLdcInsn(string) }

// Utility to pop n elements from the stack
fun MethodVisitor.pop(n: Int = 1) {
    require(n >= 1) { "Can only pop at least one element from the stack" }
    repeat(n) { visitInsn(POP) }
}

// Utility to duplicate n elements onto the stack
fun MethodVisitor.dup(n: Int = 1) {
    require(n >= 1) { "At least one duplication should be performed" }
    repeat(n) { visitInsn(DUP) }
}

// Utility to return the method
// Return type must be valid
fun MethodVisitor.returnMethod(opcode: Int = RETURN) {
    require(opcode in (IRETURN..RETURN)) { "Must be valid return" }
    visitInsn(opcode)
}

// Utililty to load a local variable or parameter
fun MethodVisitor.loadVariable(num: Int, opcode: Int = ALOAD) {
    require(opcode in (ILOAD..ALOAD)) { "Must be valid load operation" }
    visitVarInsn(opcode, num)
}

// Utililty to store a local variable or parameter
fun MethodVisitor.storeVariable(num: Int, opcode: Int = ASTORE) {
    require(opcode in (ISTORE..ASTORE)) { "Must be valid store operation" }
    visitVarInsn(opcode, num)
}

// Utility to concat something with constants
fun MethodVisitor.concat(desc: String, template: String) =
    visitInvokeDynamicInsn(concatHandle.name, desc, concatHandle, template)

// Utility to convert integer on operand stack to string
fun MethodVisitor.intToString() =
    invokeMethod(java.lang.Integer::class.java.getMethod("toString", Int::class.javaPrimitiveType))

// Utility to box an integer on operand stack
fun MethodVisitor.boxInt() =
    invokeMethod(java.lang.Integer::class.java.getMethod("valueOf", Int::class.javaPrimitiveType))

// Utility to call a super or this constructor
