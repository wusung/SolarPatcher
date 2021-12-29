package com.grappenmaker.solarpatcher.util

import org.objectweb.asm.Type
import java.lang.reflect.Method
import java.lang.reflect.Modifier

// Class naming
// Returns the fully qualified name
val Class<*>.fullyQualifiedName: String
    get() = when {
        isLocalClass -> "${enclosingClass.fullyQualifiedName}$${enclosingClass.declaredClasses.indexOf(this)}"
        isMemberClass -> "${enclosingClass.fullyQualifiedName}$$simpleName"
        else -> canonicalName
    }

// Returns the jvm internal name
val Class<*>.internalName: String get() = fullyQualifiedName.replace('.', '/')

// Gives the description of this method
fun Method.asDescription() = MethodDescription(name, Type.getMethodDescriptor(this), declaringClass.internalName)

// Give the invocation type that should be used to invoke this method
val Method.invocationType
    get() = when {
        isPrivate -> InvocationType.SPECIAL
        isStatic -> InvocationType.STATIC
        isInterfaceMethod -> InvocationType.INTERFACE
        else -> InvocationType.VIRTUAL
    }

// Accessors for methods
val Method.isPublic get() = Modifier.isPublic(modifiers)
val Method.isPrivate get() = Modifier.isPrivate(modifiers)
val Method.isProtected get() = Modifier.isProtected(modifiers)
val Method.isStatic get() = Modifier.isStatic(modifiers)
val Method.isFinal get() = Modifier.isFinal(modifiers)
val Method.isAbstract get() = Modifier.isStatic(modifiers)
val Method.isInterfaceMethod get() = declaringClass.isInterface