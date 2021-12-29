package com.grappenmaker.solarpatcher

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

class LoaderClassWriter(
    private val loader: ClassLoader,
    classReader: ClassReader? = null,
    flags: Int
) : ClassWriter(classReader, flags) {
    override fun getClassLoader() = loader
}