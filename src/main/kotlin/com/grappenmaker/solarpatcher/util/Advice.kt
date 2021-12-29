package com.grappenmaker.solarpatcher.util

import com.grappenmaker.solarpatcher.API
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.commons.AdviceAdapter

// Creates a method visitor that applies advice to a visitor
// desc: Description of the method, used in the advice adapter
// parent: the parent method visitor to apply advice on
// enterAdvice: the code to run, when the start of the method is being visited
// exitAdvice: the code to run, when the end of the method is being visited
fun createAdvice(
    desc: MethodDescription,
    parent: MethodVisitor,
    enterAdvice: MethodVisitor.() -> Unit = {},
    exitAdvice: MethodVisitor.() -> Unit = {}
) = object : AdviceAdapter(API, parent, desc.access, desc.name, desc.descriptor) {
    override fun onMethodEnter() = enterAdvice()
    override fun onMethodExit(opcode: Int) = exitAdvice()
}

// Creates a class visitor that applies advice to a given method
// parent: the parent class visitor, if any
// desc: Description of the method that will have advice applied on
// enterAdvice: the code to run, when the start of the method is being visited
// exitAdvice: the code to run, when the end of the method is being visited
class AdviceClassVisitor(
    parent: ClassVisitor,
    private val desc: MethodDescription,
    private val enterAdvice: MethodVisitor.() -> Unit = {},
    private val exitAdvice: MethodVisitor.() -> Unit = {}
) : ClassVisitor(API, parent) {
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
        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
        val thisMethod = MethodDescription(name, descriptor, owner, access)

        return if (desc.match(thisMethod)) createAdvice(thisMethod, mv, enterAdvice, exitAdvice) else mv
    }
}