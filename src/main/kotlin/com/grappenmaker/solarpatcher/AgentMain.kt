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

@file:JvmName("AgentMain")

package com.grappenmaker.solarpatcher

import com.grappenmaker.solarpatcher.asm.transform.FileTransformer
import com.grappenmaker.solarpatcher.config.Configuration
import com.grappenmaker.solarpatcher.config.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import java.io.File
import java.io.IOException
import java.lang.instrument.Instrumentation

@Suppress("unused")
fun premain(arg: String?, inst: Instrumentation) {
    // Get the config based on the args
    val filename = arg ?: "config.json"
    val config = try {
        json.decodeFromString(File(filename).readText())
    } catch (e: SerializationException) {
        e.printStackTrace()
        println("Something went wrong deserializing the config, error is above")
        println("Falling back to default config")
        Configuration()
    } catch (e: IOException) {
        e.printStackTrace()
        println("An IO error occured when loading the config, error is above")
        println("Falling back to default config")
        Configuration()
    }

    // Define transforms and visitors
    val transforms = config.getModules()
        .filter { it.isEnabled || config.enableAll }
        .also { println("Using modules ${it.joinToString { m -> m::class.simpleName ?: "Unnamed" }}") }
        .map { it.asTransform() }

    // Add them to the instrumentation backend implementation of the jvm
    inst.addTransformer(FileTransformer(transforms, debug = true))
}