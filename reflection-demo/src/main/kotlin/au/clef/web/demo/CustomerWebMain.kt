package au.clef.web.demo

import au.clef.api.reflectionApiConfig
import au.clef.api.stringScalarConverter
import au.clef.app.demo.model.*
import au.clef.engine.MethodSource
import au.clef.engine.MethodSource.Instance
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val customerService: CustomerService = CustomerService()

private val acmeService: AcmeService = AcmeService()

internal val customerReflectionConfig = reflectionConfig(
//    Instance(acmeService, "Acme Service"),
    Instance(customerService, "Customer Service"),
    MethodSource.InstanceMethod(customerService, "Customer Service", CustomerService::addCustomer),
    MethodSource.InstanceMethod(
        customerService,
        "Customer Service",
        CustomerService::normalizeEmail
    ),
    MethodSource.StaticMethod(::myAddKotlinFunction, "A kotlin function"),
//    MethodSource.StaticClass(Math::class),
    MethodSource.StaticMethod(Math::class, "Java static method", "max", Int::class, Int::class),
    MethodSource.StaticMethod(Math::class, "Minimum Of Two Numbers", "min", Int::class, Int::class),
)
    .supportingTypes(Customer::class, Address::class, Person::class)
    .build()

val apiConfig = reflectionApiConfig(customerReflectionConfig)
    .scalarConverters(
        stringScalarConverter(decodeText = ::CustomerId),
        stringScalarConverter(decodeText = ::EmailAddress)
    )
    .metadataResourcePath("/config/method-metadata.json")
    .build()

fun main() {
    WebServer(apiConfig, WebServerConfig()).start()
}