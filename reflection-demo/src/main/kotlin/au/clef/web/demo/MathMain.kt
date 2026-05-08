package au.clef.web.demo

import au.clef.api.reflectionApiConfig
import au.clef.engine.MethodSource
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

internal val mathConfig = reflectionConfig(
    MethodSource.StaticClass(Math::class)
)
    .build()

val mathApiConfig = reflectionApiConfig(mathConfig)
    // todo generate metadata
//    .metadataResourcePath("/config/math-metadata.json")
    .build()

fun main() {
    WebServer(mathApiConfig, WebServerConfig()).start()
}