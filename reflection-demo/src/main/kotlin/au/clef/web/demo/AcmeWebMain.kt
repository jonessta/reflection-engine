package au.clef.web.demo

import au.clef.app.demo.AcmeDemoConfig
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.engine.ExposedTarget
import au.clef.web.ReflectionServiceApi
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

fun main() {
    val reflectionService = ReflectionServiceApi(
        targets = AcmeDemoConfig.targets,
        targetSupportingTypes = AcmeDemoConfig.targetSupportingTypes,
        metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
    )
    WebServer(reflectionService, WebServerConfig()).start()
}
