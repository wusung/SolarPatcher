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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

// Utility function to add the updater task to the project
// This task creates a updater.json file, which is used by
// the launcher auto updater
fun Project.addUpdaterTask() {
    val updaterTask = createUpdaterTask()
    tasks.named<DefaultTask>("build") { dependsOn(updaterTask.path) }
}

// Internally create the updater task
private fun Project.createUpdaterTask() = tasks.create("updaterConfig") {
    dependsOn("jar")
    onlyIf { !getJarTask().state.upToDate }

    doLast {
        // Save updater with built artifact data
        val file = getJarTask().archiveFile.get().asFile

        // Create sha1 hash
        val md = MessageDigest.getInstance("SHA-1")
        DigestInputStream(file.inputStream(), md).readBytes()
        val hash = md.digest()

        // Compute hex representation of hash
        val hexHash = hash.joinToString("") { "%02x".format(it) }

        // Save config
        // Yes, i really hardcoded the json here, why would i use
        // an overkill serialization lib?
        val json = """
            {
                "version": "$version",
                "filename": "${file.name}",
                "sha1": "$hexHash"
            }
        """.trimIndent()
        File(buildDir, Constants.updaterConfig).writeText(json)
    }
}

// Util to get the jar task
private fun Project.getJarTask() = tasks.named<Jar>("jar").get()