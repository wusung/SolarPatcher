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

import org.gradle.api.Project
import java.io.File
import java.nio.file.Files

fun Project.addVersioningTask(resourcesDir: File) = tasks.create("versioning") {
    doLast {
        if (!resourcesDir.exists()) Files.createDirectories(resourcesDir.toPath())
        val properties = mapOf(
            "version" to version,
            "buildTimestamp" to System.currentTimeMillis(),
            "devBuild" to !gradle.taskGraph.allTasks.any { it.name.endsWith("buildProd") }
        )

        File(resourcesDir, "versions.txt").writeText(properties.map { (key, value) -> "$key=$value" }
            .joinToString(System.lineSeparator()))
    }
}.also { tasks.named("classes").get().dependsOn(it.path) }