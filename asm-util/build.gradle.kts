plugins {
    // Setup kotlin
    kotlin("jvm")

    // Setup serialization
    kotlin("plugin.serialization")

    // Because of the library aspect, also allow publishing
    `maven-publish`
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.grappenmaker"
            artifactId = "asm-util"
            version = version

            from(components["kotlin"])
        }
    }
}