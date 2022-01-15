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

import org.objectweb.asm.Type
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

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

// Implementations for kclass too
val KClass<*>.fullyQualifiedName: String get() = java.fullyQualifiedName
val KClass<*>.internalName: String get() = java.internalName

// Get names with reified generic
inline fun <reified T> getFullName() = T::class.fullyQualifiedName
inline fun <reified T> getInternalName() = T::class.internalName

// Gives the description of this method
fun Method.asDescription() =
    MethodDescription(name, Type.getMethodDescriptor(this), declaringClass.internalName, modifiers)

fun Method.asMatcher(): MethodMatcher = matchDescription(asDescription())

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