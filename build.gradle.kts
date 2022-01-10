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
    val kotlinVersion = "1.6.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
}

group = "com.grappenmaker"
version = "1.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
    implementation("org.ow2.asm:asm:9.2")
    implementation("org.ow2.asm:asm-commons:9.2")
    implementation("org.ow2.asm:asm-util:9.2")
}

tasks.jar {
    manifest {
        attributes("Premain-Class" to "com.grappenmaker.solarpatcher.AgentMain")
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.create("saveDefaultConfig") {
    dependsOn("classes")
    doLast {
        val classes = sourceSets.main.get().output.classesDirs.map { it.toURI().toURL() }
        val classLoader = URLClassLoader(
            (classes + configurations.runtimeClasspath.get().map { it.toURI().toURL() }).toTypedArray(),
            ClassLoader.getSystemClassLoader()
        )

        val args = arrayOf("config.example.json")
        Class.forName("com.grappenmaker.solarpatcher.config.SaveDefaultConfig", true, classLoader)
            .getMethod("main", args::class.java)(null, args)
    }
}