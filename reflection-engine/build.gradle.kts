plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}
