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

@file:JvmName("ModIdExtractor")

package com.grappenmaker.solarpatcher.util

import com.grappenmaker.solarpatcher.asm.constants
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import java.io.File
import java.util.jar.JarFile
import kotlin.system.measureTimeMillis

// Utility to extract so-called "feature ids" from a lunar-prod-optifine.jar file
// This is done by loading all classes, finding the FeatureDetails class by looking for a constant,
// and then finding classes that yield defined FeatureDetails.
// The constant pools of the methods that return the FeatureDetails should start with a String.
// This string is the feature id
// Note: streaming (like with > and |) is supported because debug logs are sent to the STDERR

// Usage:
// java -cp solar-patcher-vX.X.jar com.grappenmaker.solarpatcher.util.ModIdExtractor /path/to/lunar-prod-optifine.jar
// And you could add "> mods.txt" to save this to a file.
fun main(args: Array<String>) {
    val fileName = args.firstOrNull() ?: "lunar-prod-optifine.jar"
    val file = File(fileName)

    if (!file.exists()) {
        debugLog("File $fileName does not exist! Please provide a valid file.")
        return
    }

    val tookMs = measureTimeMillis {
        debugLog("""Loading classes from jarfile "${file.canonicalPath}"...""")

        val jar = JarFile(file)
        val classes = jar.entries().asSequence()
            .filter { it.name.startsWith("lunar/") && it.name.endsWith(".lclass") }
            .map { e ->
                ClassNode().also {
                    ClassReader(jar.getInputStream(e).readBytes()).accept(it, ClassReader.SKIP_DEBUG)
                }
            }.toList()

        debugLog("${classes.size} classes loaded (as ClassNodes).")
        debugLog("Finding main class...")

        val mainClass = classes.find { it.constants.contains("Starting Lunar client...") }
            ?: error("Cannot find LC main class")

        val clinit = mainClass.methods.find { it.name == "<clinit>" } ?: error("No clinit was found?")
        val setVersion = clinit.instructions.filterIsInstance<FieldInsnNode>().find { it.name == "version" }
            ?: error("Version field was not set")

        val version = (setVersion.previous as LdcInsnNode).cst

        debugLog("Main class is ${mainClass.name}")
        debugLog("Lunar Client version is $version")

        debugLog("Finding FeatureDetails class...")
        val featureDetails =
            classes.find { it.constants.contains("FeatureDetails(id=\u0001, " +
                    "categories=\u0001, " +
                    "enabledOnCurrentVersion=\u0001, " +
                    "aliases=\u0001, " +
                    "originalAuthors=\u0001)") }
                ?: error("No featuredetails was found")

        debugLog("Finding implementations of FeatureDetails method...")
        val featureDetailsImpls = classes.mapNotNull { c ->
            c.methods.find {
                it.desc == "()L${featureDetails.name};"
                        && it.access and Opcodes.ACC_PROTECTED != 0
            }
        }

        debugLog("Extracting mod ids...")

        val modIds = featureDetailsImpls.mapNotNull { it.constants.firstOrNull() as? String }.sorted()
        println(modIds.joinToString(System.lineSeparator()))
    }

    println("Took ${tookMs}ms")
}

fun debugLog(msg: String) = System.err.println(msg)
