@file:JvmName("AgentMain")

package com.grappenmaker.solarpatcher

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.instrument.Instrumentation

@Suppress("unused")
fun premain(arg: String?, inst: Instrumentation) {
    // Get the config based on the args
    val filename = arg ?: "config.json"
    val config = try {
        Json.decodeFromString(File(filename).readText())
    } catch (e: Exception) {
        e.printStackTrace()
        println("Something went wrong loading the config, error is above")
        println("Falling back to default config")
        Configuration()
    }

    // Define transforms and visitors
    val transforms = config.getModules()
//        .filter { it.isEnabled }
        .map { it.asTransform() }

    // Add them to the instrumentation backend implementation of the jvm
    inst.addTransformer(FileTransformer(transforms))
}