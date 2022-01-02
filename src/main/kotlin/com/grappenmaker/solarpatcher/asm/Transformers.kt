package com.grappenmaker.solarpatcher.asm

import com.grappenmaker.solarpatcher.asm.util.ClassVisitorWrapper
import com.grappenmaker.solarpatcher.asm.util.MethodVisitorWrapper
import com.grappenmaker.solarpatcher.config.API
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
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
        return transforms.filter { it.matcher.matches(MethodDescription(name, descriptor, owner, access)) }
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
abstract class MethodTransform(val matcher: MethodMatcher) {
    abstract fun asVisitor(parent: MethodVisitor): MethodVisitor
}

// Transformer to search-and-replace text in a given method
class TextTransform(
    matcher: MethodMatcher,
    val from: String,
    val to: String
) : MethodTransform(matcher) {
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
    matcher: MethodMatcher,
    val transformedMatcher: MethodMatcher
) : MethodTransform(matcher) {
    abstract val replacement: MethodVisitor.(opcode: Int, description: MethodDescription) -> Unit

    override fun asVisitor(parent: MethodVisitor) = object : MethodVisitor(API, parent) {
        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            val calledMethod = MethodDescription(name, descriptor, owner)
            if (transformedMatcher.matches(calledMethod)) {
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
    matcher: MethodMatcher,
    invokedMatcher: MethodMatcher,
    newMethod: MethodDescription,
    invocationType: InvocationType,
    isInterface: Boolean = invocationType == InvocationType.INTERFACE,
    prepare: MethodVisitor.() -> Unit = {}
) : InvokeTransform(matcher, invokedMatcher) {
    override val replacement: MethodVisitor.(Int, MethodDescription) -> Unit = { _, _ ->
        prepare()

        val (name, descriptor, owner) = newMethod
        visitMethodInsn(invocationType.opcode, owner, name, descriptor, isInterface)
    }

    companion object {
        // Constructs a transformer that replaces the invocation of a method
        // with a given Method, with a preperation block
        fun fromMethod(
            matcher: MethodMatcher,
            invokedMatcher: MethodMatcher,
            method: Method,
            prepare: (MethodVisitor) -> Unit = {}
        ) = ReplaceInvokeTransform(
            matcher,
            invokedMatcher,
            method.asDescription(),
            method.invocationType,
            method.isStatic,
            prepare
        )
    }
}

// Transformer to replace a method with a code block
open class ReplaceCodeTransform(
    matcher: MethodMatcher,
    invokedMatcher: MethodMatcher,
    override val replacement: MethodVisitor.(Int, MethodDescription) -> Unit,
) : InvokeTransform(matcher, invokedMatcher)

// Transformer to remove a method call
class RemoveInvokeTransform(
    matcher: MethodMatcher,
    invokedMatcher: MethodMatcher,
    popCount: Int = 0
) : ReplaceCodeTransform(matcher, invokedMatcher, { _, _ -> if (popCount > 0) pop(popCount) })

// Transformer to add code before and/or after a method call
class InvokeAdviceTransform(
    matcher: MethodMatcher,
    invokedMatcher: MethodMatcher,
    val beforeAdvice: MethodVisitor.() -> Unit = {},
    val afterAdvice: MethodVisitor.() -> Unit = {}
) : InvokeTransform(matcher, invokedMatcher) {
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
    matcher: MethodMatcher,
    val implementation: MethodVisitor.() -> Unit
) : MethodTransform(matcher) {
    // Ignoring parent, won't delegate
    override fun asVisitor(parent: MethodVisitor) = object : MethodVisitor(API, null) {
        override fun visitCode() {
            parent.visitCode()
            parent.implementation()
            parent.visitMaxs(-1, -1)
            parent.visitEnd()
        }

        override fun visitParameter(name: String, access: Int) {
            parent.visitParameter(name, access)
        }
    }
}

// Transfomer to replace the method with a stub method
class StubMethodTransform(desc: MethodDescription) : ImplementTransform(desc.asMethodMatcher(), {
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
    matcher: MethodMatcher,
    val visitor: MethodVisitorWrapper
) : MethodTransform(matcher) {
    override fun asVisitor(parent: MethodVisitor) = visitor(parent)
}
