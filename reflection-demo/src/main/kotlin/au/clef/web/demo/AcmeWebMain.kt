package au.clef.web.demo

import au.clef.api.ReflectionApiConfig
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.MethodSource.*
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val acmeService = AcmeService()

internal val acmeConfig = reflectionConfig(
    InstanceMethod(acmeService, "ACME Service", "personName", Person::class),
    StaticMethod(::add),
    StaticMethod(Math::class, "min", Int::class, Int::class),
    StaticMethod(Math::class, "max", Int::class, Int::class)
)
    .supportingTypes(Person::class, Address::class)
    .metadataResourcePath("/config/acme-metadata.json")
    .build()

val apiConfig: ReflectionApiConfig = ReflectionApiConfig(acmeConfig)
val webConfig = WebServerConfig(port = 8080, host = "0.0.0.0")

fun main() {
    // todo provide a constructor which just takes ReflectionCOnfig ie without user mappings if not needed
    WebServer(apiConfig, webConfig).start()
}