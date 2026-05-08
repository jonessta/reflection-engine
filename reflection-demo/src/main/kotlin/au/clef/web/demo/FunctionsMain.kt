package au.clef.web.demo

import au.clef.api.reflectionApiConfig
import au.clef.app.demo.model.*
import au.clef.engine.MethodSource
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val customerService: CustomerService = CustomerService()

private  val functionsConfig = reflectionConfig(
    // An instance method
    MethodSource.InstanceMethod(customerService, "Customer Service", CustomerService::addCustomer),

    // Static functions
    MethodSource.StaticMethod(::myAddKotlinFunction, "A kotlin function"),
    MethodSource.StaticMethod(Math::class, "Java static method", "max", Int::class, Int::class),
)
    .supportingTypes(Customer::class, Address::class, Person::class, ZipCode::class)
    .build()

val functionsApiConfig = reflectionApiConfig(functionsConfig)
//    .metadataResourcePath("/config/method-metadata.json")
    .build()

fun main() {
    WebServer(functionsApiConfig, WebServerConfig()).start()
}