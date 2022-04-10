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
    kotlin("jvm")
}

// Set metadata
group = "com.grappenmaker"
version = Versions.project

// Enable mavenCentral
repositories {
    mavenCentral()
}

// Declare dependencies
dependencies {
    // Depend on asm-util
    implementation(project(":asm-util"))

    // BSDIFF implementation for java (for LunarMapper)
    implementation("io.sigpipe:jbsdiff:1.0")

    // HOCON parser (for the launcher)
    implementation("com.typesafe:config:1.4.2")
}

// Add dependencies to jar task
tasks.withType<Jar>().configureEach {
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

fun createRunTask(name: String, clazz: String, block: JavaExec.() -> Unit = {}) =
    rootProject.tasks.create<JavaExec>(name) {
        val jarTask = tasks.jar.get()
        dependsOn(jarTask.path)
        classpath(jarTask.outputs.files.map { it.absolutePath })
        mainClass.set("com.grappenmaker.solarpatcher.util.$clazz")

        block()
    }

createRunTask("runMapper", "LunarMapper")
createRunTask("runLauncher", "LunarLauncher") {
    doFirst {
        systemProperties(
            "java.library.path" to (properties["natives"]
                ?: error("Specify the natives path!"))
        )
    }
}

createRunTask("runExtractor", "ModIdExtractor")
