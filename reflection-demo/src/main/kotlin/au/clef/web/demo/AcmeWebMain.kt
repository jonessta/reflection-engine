package au.clef.web.demo

import au.clef.api.ReflectionApiConfig
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.MethodSource
import au.clef.engine.MethodSource.StaticMethod
import au.clef.engine.ReflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val acmeServiceInstance = AcmeService()

//private val reflectionConfig = ReflectionConfig(
//    methodSources = listOf(
//        MethodSource.InstanceMethod.from(
//            instance = acmeServiceInstance,
//            instanceDescription = "ACME Service",
//            methodName = "personName",
//            Person::class
//        ),
//        StaticMethod.from(::add),
//        StaticMethod.from(Math::class, "max", Int::class, Int::class),
//        StaticMethod.from(Math::class, "min", Int::class, Int::class)
//    ),
//    methodSupportingTypes = listOf(Person::class, Address::class),
//    metadataResourcePath = "/config/acme-metadata.json"
//)
//
//private val reflectionConfig = ReflectionConfig(
//    methodSource = MethodSource.StaticClass(Math::class),
//    metadataResourcePath = "/config/acme-metadata.json",
//)

val reflectionConfig = ReflectionConfig(
    methodSource = MethodSource.Instance(acmeServiceInstance, "ACME Service"),
    methodSupportingTypes = listOf(Person::class, Address::class),
    metadataResourcePath = "/config/acme-metadata.json"
)

val apiConfig = ReflectionApiConfig(
    reflectionConfig = reflectionConfig,
    userDefinedScalarConverters = emptyList()
)

val webConfig = WebServerConfig(port = 8080, host = "0.0.0.0")

fun main() {
    WebServer(apiConfig, webConfig).start()
}