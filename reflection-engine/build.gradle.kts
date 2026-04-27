plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
