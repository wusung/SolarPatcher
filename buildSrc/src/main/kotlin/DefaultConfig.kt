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
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.named
import java.net.URLClassLoader

// Create task to save the default configuration
// Useful before pushing
fun Project.addSaveDefaultConfigTask() = tasks.create("saveDefaultConfig") {
    dependsOn("classes")
    doLast {
        // Sets up classloader and classpath
        val classes = getSourceSets().main.get().output.classesDirs.map { it.toURI().toURL() }
        val classLoader = URLClassLoader(
            (classes + getRuntimeClassPath().map { it.toURI().toURL() }).toTypedArray(),
            ClassLoader.getSystemClassLoader()
        )

        // Calls the main method with the default config file
        val args = arrayOf(Constants.defaultConfig)
        Class.forName(Constants.saveConfigClass, true, classLoader)
            .getMethod("main", args::class.java)(null, args)
    }
}

private fun Project.getRuntimeClassPath() = configurations.getByName("runtimeClasspath")
private fun Project.getSourceSets() = extensions.getByName("sourceSets") as SourceSetContainer
private val SourceSetContainer.main get() = named<SourceSet>("main")