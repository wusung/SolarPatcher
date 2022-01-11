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

import java.net.URLClassLoader

plugins {
    // Setup kotlin
    kotlin("jvm") version Versions.kotlin

    // Setup serialization
    kotlin("plugin.serialization") version Versions.kotlin

    // Setup static analysis with detekt
    id("io.gitlab.arturbosch.detekt") version Versions.detekt
}

// Set metadata
group = "com.grappenmaker"
version = "1.2"

// Enable mavenCentral
repositories {
    mavenCentral()
}

// Declare dependencies
dependencies {
    // Kotlin dependencies
    implementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serializationJSON}")

    // ASM
    implementation("org.ow2.asm:asm:${Versions.asm}")
    implementation("org.ow2.asm:asm-commons:${Versions.asm}")
    implementation("org.ow2.asm:asm-util:${Versions.asm}")
}

// Add dependencies and manifest to jar task
tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Premain-Class" to Constants.premainClass)
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Create task to save the default configuration
// Useful before pushing
tasks.create("saveDefaultConfig") {
    dependsOn("classes")
    doLast {
        // Sets up classloader and classpath
        val classes = sourceSets.main.get().output.classesDirs.map { it.toURI().toURL() }
        val classLoader = URLClassLoader(
            (classes + configurations.runtimeClasspath.get().map { it.toURI().toURL() }).toTypedArray(),
            ClassLoader.getSystemClassLoader()
        )

        // Calls the main method with the default config file
        val args = arrayOf(Constants.defaultConfig)
        Class.forName(Constants.saveConfigClass, true, classLoader)
            .getMethod("main", args::class.java)(null, args)
    }
}
// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    config = files("detekt.yml")
}

// Shortcut to run detekt
tasks.create("lint") {
    dependsOn("detekt")
}