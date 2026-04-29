package au.clef.web.demo

import au.clef.api.reflectionApiConfig
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.MethodSource.*
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val acmeService = AcmeService()

private val reflectionConfig1 = reflectionConfig(
    InstanceMethod(acmeService, "ACME Service", "personName", Person::class),
    StaticMethod(::add),
    StaticMethod(Math::class, "min", Int::class, Int::class),
    StaticMethod(Math::class, "max", Int::class, Int::class)
)
    .supportingTypes(Person::class, Address::class)
    .metadataResourcePath("/config/acme-metadata.json")
    .build()

private val reflectionConfig = reflectionConfig(
    Instance(instance = acmeService, instanceDescription = "myService")
)
    .supportingTypes(Person::class, Address::class)
    .metadataResourcePath("/config/acme-metadata.json")
    .build()

private val apiConfig = reflectionApiConfig(reflectionConfig)
//    .scalarConverter()
    .build()

val webConfig = WebServerConfig(port = 8080, host = "0.0.0.0")

fun main() {
    WebServer(apiConfig, webConfig).start()
}