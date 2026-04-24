plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "kotlin-reflection"
include("reflection-engine")
include("reflection-engine-web")
include("reflection-demo")