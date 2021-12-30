package com.grappenmaker.solarpatcher.asm

import com.grappenmaker.solarpatcher.asm.util.LoaderClassWriter
import com.grappenmaker.solarpatcher.asm.util.toVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

// A ClassFileTransformer that applies ClassTransforms
class FileTransformer(
    private val transforms: List<ClassTransform> = listOf(),
    private val debug: Boolean = false,
    private val removeDebugInfo: Boolean = !debug
) : ClassFileTransformer {
    override fun transform(
        loader: ClassLoader?,
        className: String,
        classBeingRedefined: Class<*>?,
        domain: ProtectionDomain,
        classFile: ByteArray
    ): ByteArray? {
        val classTransform = transforms.getFromName(className) ?: return null

        // Not interested to transform if there are no transforms or visitors
        if (classTransform.methodTransforms.isEmpty() && classTransform.visitors.isEmpty()) return null
        val actualLoader = loader ?: return null // Not interested in transforming classes from bootstrap loader

        val reader = ClassReader(classFile)
        val writer = LoaderClassWriter(actualLoader, reader, ClassWriter.COMPUTE_FRAMES)

        try {
//            val parent = if (debug) TraceClassVisitor(writer, PrintWriter(System.out)) else writer
//            val visitor = classTransform.visitors.toVisitor(parent)
            val visitor = classTransform.visitors.toVisitor(writer)
            val options = (if (removeDebugInfo) ClassReader.SKIP_DEBUG else 0) or
                    if (classTransform.shouldExpand) ClassReader.EXPAND_FRAMES else 0

            reader.accept(TransformVisitor(classTransform.methodTransforms, visitor), options)
        } catch (e: Exception) {
            println("Error while transforming: $e")
            e.printStackTrace()
            return null
        }

        return writer.toByteArray().also {
            if (debug) {
                println("Result of transforming $className (applying ${classTransform.methodTransforms.size} transforms)")
                println()
                ClassReader(it).accept(TraceClassVisitor(PrintWriter(System.out)), 0)
            }
        }
    }
}

// Utility to get a class transform based on the name
private fun Collection<ClassTransform>.getFromName(className: String) =
    filter { it.className == className }.reduceOrNull { acc, cur ->
        ClassTransform(
            className,
            acc.methodTransforms + cur.methodTransforms,
            acc.visitors + cur.visitors,
            acc.shouldExpand || cur.shouldExpand
        )
    }