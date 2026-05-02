package au.clef.web.demo

import au.clef.api.ReflectionApiConfig
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.myAddKotlinFunction
import au.clef.engine.MethodSource.InstanceMethod
import au.clef.engine.MethodSource.StaticMethod
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val acmeService = AcmeService()

internal val acmeConfig = reflectionConfig(
    InstanceMethod(acmeService, "ACME Service", "personName", Person::class),
    StaticMethod(::myAddKotlinFunction),
    StaticMethod(Math::class, "min", Int::class, Int::class),
    StaticMethod(Math::class, "max", Int::class, Int::class)
)
    .supportingTypes(Person::class, Address::class)
    .metadataResourcePath("/config/acme-metadata.json")
    .build()

val webConfig = WebServerConfig()
val config = ReflectionApiConfig(acmeConfig)
fun main() {
    WebServer(config, webConfig).start()
}