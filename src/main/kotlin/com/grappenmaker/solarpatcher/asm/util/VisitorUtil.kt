package com.grappenmaker.solarpatcher.asm.util

import org.objectweb.asm.*

// Visitor wrappers, used for chaining visitors before we actually have the visitors
typealias ClassVisitorWrapper = (parent: ClassVisitor) -> ClassVisitor
typealias ModuleVisitorWrapper = (parent: ModuleVisitor) -> ModuleVisitor
typealias AnnotationVisitorWrapper = (parent: AnnotationVisitor) -> AnnotationVisitor
typealias RecordComponentVisitorWrapper = (parent: RecordComponentVisitor) -> RecordComponentVisitor
typealias FieldVisitorWrapper = (parent: FieldVisitor) -> FieldVisitor
typealias MethodVisitorWrapper = (parent: MethodVisitor) -> MethodVisitor

private fun <T> Collection<(T) -> T>.foldWrappers(parent: T) = fold(parent) { acc, cur -> cur(acc) }

fun Collection<ClassVisitorWrapper>.toVisitor(parent: ClassVisitor) = foldWrappers(parent)
fun Collection<ModuleVisitorWrapper>.toVisitor(parent: ModuleVisitor) = foldWrappers(parent)
fun Collection<AnnotationVisitorWrapper>.toVisitor(parent: AnnotationVisitor) = foldWrappers(parent)
fun Collection<RecordComponentVisitorWrapper>.toVisitor(parent: RecordComponentVisitor) = foldWrappers(parent)
fun Collection<FieldVisitorWrapper>.toVisitor(parent: FieldVisitor) = foldWrappers(parent)
fun Collection<MethodVisitorWrapper>.toVisitor(parent: MethodVisitor) = foldWrappers(parent)