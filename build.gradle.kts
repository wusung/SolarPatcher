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
version = "1.3"

// Enable mavenCentral
repositories {
    mavenLocal()
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
        attributes(
            "Main-Class" to "com.grappenmaker.solarpatcher.NotRunnableNotice",
            "Premain-Class" to Constants.premainClass
        )
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Custom tasks to make life easier
// See docs of individual tasks
val resourcesDir = sourceSets.main.get().output.resourcesDir
addVersioningTask(resourcesDir ?: error("No resources output dir found"))
addSaveDefaultConfigTask()
addUpdaterTask()

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    config = files("detekt.yml")
}

// Shortcut to run detekt
tasks.create("lint") {
    dependsOn("detekt")
}

// Configure detekt to always run (not cache)
tasks.detekt {
    outputs.upToDateWhen { false }
}