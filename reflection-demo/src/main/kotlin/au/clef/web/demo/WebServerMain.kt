package au.clef.web.demo

import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.web.ReflectionServiceApi
import au.clef.web.WebServer
import au.clef.web.WebServerConfig
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
