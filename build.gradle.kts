plugins {
    kotlin("jvm") version "2.3.10" apply false
    kotlin("plugin.serialization") version "2.3.10" apply false
}

group = "au.clef"
version = "1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}