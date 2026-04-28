plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":reflection-engine"))
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}