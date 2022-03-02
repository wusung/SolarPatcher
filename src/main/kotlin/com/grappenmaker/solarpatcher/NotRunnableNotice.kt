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

@file:JvmName("NotRunnableNotice")

package com.grappenmaker.solarpatcher

// Used to instruct the user that solar-patcher.jar is not runnable
fun main() {
    println("""
        |Thanks for using Solar Patcher!
        |
        |Because this patcher is actually a runtime agent, just running this will not work.
        |Try downloading Solar Tweaks, or launch Lunar Client with a custom launcher
        |
        |Add the following argument: "-javaagent:solar-patcher-${Versioning.version}.jar=/path/to/config.json",
        |where you replace the path with the path of a configuration file.
        |
        |For more information, go to https://github.com/Solar-Tweaks/SolarPatcher or join our Discord server
    """.trimMargin())
}
