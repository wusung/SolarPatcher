@file:JvmName("SaveDefaultConfig")

package com.grappenmaker.solarpatcher

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json {
    encodeDefaults = true
    prettyPrint = true
}

fun main(args: Array<String>) {
    val filename = args.firstOrNull() ?: "config.json"
    println("Saving default config to $filename")
    File(filename).writeText(json.encodeToString(Configuration()))
}