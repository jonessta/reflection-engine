package au.clef.web.demo

import au.clef.api.reflectionApiConfig
import au.clef.api.stringScalarConverter
import au.clef.app.demo.model.*
import au.clef.engine.MethodSource.Instance
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val customerService: CustomerService = CustomerService()

internal val customerReflectionConfig = reflectionConfig(
    Instance(customerService, "Customer Service"),
)
    .supportingTypes(Customer::class, Address::class, Person::class, ZipCode::class)
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