plugins {
    // Setup kotlin
    kotlin("jvm")

    // Setup serialization
    kotlin("plugin.serialization")
}

group = "com.grappenmaker"
version = Versions.project

repositories {
    mavenCentral()
}

dependencies {
    // kotlin-reflect
    api("org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}")

    // ASM
    api("org.ow2.asm:asm:${Versions.asm}")
    api("org.ow2.asm:asm-commons:${Versions.asm}")
    api("org.ow2.asm:asm-util:${Versions.asm}")

    // Kotlin dependencies
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serializationJSON}")
}