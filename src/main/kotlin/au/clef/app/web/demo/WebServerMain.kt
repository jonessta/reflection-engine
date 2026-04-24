package au.clef.app.web.demo

import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.app.web.ReflectionServiceApi
import au.clef.app.web.WebServer
import au.clef.app.web.WebServerConfig
import au.clef.engine.ExposedTarget

fun main() {
    val config = WebServerConfig()
    val reflectionService = ReflectionServiceApi(
        target = ExposedTarget.Instance("acmeService", AcmeService()),
        targetSupportingTypes = listOf(Person::class, Address::class),
        metadataResourcePath = "/config/method-metadata.json"
    )
    WebServer(reflectionService, config).start()
}
