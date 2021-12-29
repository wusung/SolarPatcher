package com.grappenmaker.solarpatcher.util

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.PrintStream
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

val outField: Field by lazy { System::class.java.getField("out") }
val printlnMethod: Method by lazy { PrintStream::class.java.getMethod("println", String::class.java) }
val printMethod: Method by lazy { PrintStream::class.java.getMethod("print", String::class.java) }

// Makes a methodvisitor invoke a given method
// Caller is responsible for providing arguments and a target, if applicable
fun MethodVisitor.invokeMethod(method: Method) = visitMethodInsn(
    method.invocationType.opcode,
    method.declaringClass.internalName,
    method.name,
    Type.getMethodDescriptor(method),
    method.declaringClass.isInterface
)

fun MethodVisitor.invokeMethod(invocationType: InvocationType, methodDescription: MethodDescription) = visitMethodInsn(
    invocationType.opcode,
    methodDescription.owner,
    methodDescription.name,
    methodDescription.descriptor,
    invocationType == InvocationType.INTERFACE
)

// Makes a methodvisitor get the value of a field, and store it on the operand stack
// Caller is responsible for providing a target, if applicable
fun MethodVisitor.getField(field: Field) =
    visitFieldInsn(
        if (Modifier.isStatic(field.modifiers)) Opcodes.GETSTATIC else Opcodes.GETFIELD,
        field.declaringClass.internalName,
        field.name,
        Type.getDescriptor(field.type)
    )

// Makes a methodvisitor get set value of a field
// Caller is responsible for providing a value and target
fun MethodVisitor.setField(field: Field) =
    visitFieldInsn(
        if (Modifier.isStatic(field.modifiers)) Opcodes.PUTSTATIC else Opcodes.PUTFIELD,
        field.declaringClass.internalName,
        field.name,
        Type.getDescriptor(field.type)
    )

// Makes a methodvisitor construct a class with a given constructor.
// New instance will be pushed onto the stack
// Arguments for the constructor are initialized with the given block
inline fun MethodVisitor.construct(constructor: Constructor<*>, block: MethodVisitor.() -> Unit = {}) {
    visitTypeInsn(Opcodes.NEW, constructor.declaringClass.internalName)
    visitInsn(Opcodes.DUP)
    block()
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        constructor.declaringClass.internalName,
        "<init>",
        Type.getConstructorDescriptor(constructor),
        false
    )
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
    repeat(n) { visitInsn(Opcodes.POP) }
}

// Utility to return the method
// Return type must be valid
fun MethodVisitor.returnMethod(opcode: Int = Opcodes.RETURN) {
    require(opcode in (172..177)) { "Must be valid return" }
    visitInsn(opcode)
}

// Utililty to load a local variable or parameter
fun MethodVisitor.loadVariable(num: Int, opcode: Int = Opcodes.ALOAD) = visitVarInsn(opcode, num)