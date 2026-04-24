plugins {
    kotlin("jvm") version "2.3.20-RC"
    kotlin("plugin.serialization") version "2.3.20-RC"
}

group = "au.clef"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

allprojects {
    repositories {
        mavenCentral()
    }
}