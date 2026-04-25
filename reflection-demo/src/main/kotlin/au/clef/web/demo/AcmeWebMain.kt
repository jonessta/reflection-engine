package au.clef.web.demo

import au.clef.app.demo.AcmeDemoConfig
import au.clef.web.ReflectionServiceApi
import au.clef.web.WebServer
import au.clef.web.WebServerConfig


import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.add
import au.clef.engine.ExposedTarget

fun main() {
    val reflectionService = ReflectionServiceApi(
        targets = listOf(
            ExposedTarget.Instance(obj = AcmeService(), id = "acmeService"),
            ExposedTarget.StaticMethod.from(::add),
            ExposedTarget.StaticMethod.from(Math::class, "max", Int::class, Int::class)
        ),
        targetSupportingTypes = AcmeDemoConfig.targetSupportingTypes,
        metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
    )

    WebServer(reflectionService, WebServerConfig()).start()
}
