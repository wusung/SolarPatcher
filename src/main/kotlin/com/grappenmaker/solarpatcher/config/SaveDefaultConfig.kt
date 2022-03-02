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

@file:JvmName("SaveDefaultConfig")

package com.grappenmaker.solarpatcher.config

import kotlinx.serialization.encodeToString
import java.io.File

// Allows you to create a "default configuration" that is used by the launcher to configure solar patcher
fun main(args: Array<String>) {
    val filename = args.firstOrNull() ?: "config.json"
    println("Saving default config to $filename")
    File(filename).writeText(json.encodeToString(Configuration()))
}