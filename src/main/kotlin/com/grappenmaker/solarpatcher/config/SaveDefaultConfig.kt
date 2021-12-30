@file:JvmName("SaveDefaultConfig")

package com.grappenmaker.solarpatcher.config

import kotlinx.serialization.encodeToString
import java.io.File

fun main(args: Array<String>) {
    val filename = args.firstOrNull() ?: "config.json"
    println("Saving default config to $filename")
    File(filename).writeText(json.encodeToString(Configuration()))
}