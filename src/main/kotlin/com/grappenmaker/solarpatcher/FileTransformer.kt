package com.grappenmaker.solarpatcher

import com.grappenmaker.solarpatcher.util.ClassTransform
import com.grappenmaker.solarpatcher.util.TransformVisitor
import com.grappenmaker.solarpatcher.util.toVisitor
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
        if (classTransform.methodTransformers.isEmpty() && classTransform.visitors.isEmpty()) return null
        val actualLoader = loader ?: return null // Not interested in transforming classes from bootstrap loader

        val reader = ClassReader(classFile)
        val writer = LoaderClassWriter(actualLoader, reader, ClassWriter.COMPUTE_FRAMES)

        val parent = if (debug) TraceClassVisitor(writer, PrintWriter(System.out)) else writer
        val visitor = classTransform.visitors.toVisitor(parent)

        try {
            val options = (if (removeDebugInfo) ClassReader.SKIP_DEBUG else 0) or
                    if (classTransform.shouldExpand) ClassReader.EXPAND_FRAMES else 0

            reader.accept(TransformVisitor(classTransform.methodTransformers, visitor), options)
        } catch (e: Exception) {
            println("Error while transforming: $e")
            e.printStackTrace()
            return null
        }

        return writer.toByteArray()
    }
}

// Utility to get a class transform based on the name
private fun Collection<ClassTransform>.getFromName(className: String) =
    filter { it.className == className }.reduceOrNull { acc, cur ->
        ClassTransform(
            className,
            acc.methodTransformers + cur.methodTransformers,
            acc.visitors + cur.visitors,
            acc.shouldExpand || cur.shouldExpand
        )
    }