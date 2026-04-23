package au.clef.app.web

import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.engine.ExposedTarget
import au.clef.engine.ReflectionAppDefinition
import au.clef.engine.ReflectionRuntime
import au.clef.engine.createReflectionRuntime

fun main() {
    val demoDefinition = ReflectionAppDefinition(
        targets = listOf(
            ExposedTarget.Instance("acmeService", AcmeService()),
        ),
        supportingTypes = listOf(
            Person::class,
            Address::class
        ),
        metadataResourcePath = "/config/method-metadata.json"
    )

    val runtime: ReflectionRuntime = createReflectionRuntime(demoDefinition)
    val config = WebServerConfig()
    WebServer(runtime.api, config).start()
}