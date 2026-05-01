package au.clef.web.demo

import au.clef.api.reflectionApiConfig
import au.clef.api.scalarConverter
import au.clef.app.demo.model.*
import au.clef.engine.MethodSource
import au.clef.engine.MethodSource.InstanceMethod
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val customerService = CustomerService()

internal val customerReflectionConfig = reflectionConfig(
//    InstanceMethod(customerService, "Customer Service", "findCustomer", CustomerId::class),
//    InstanceMethod(customerService, "Customer Service", "normalizeEmail", EmailAddress::class)
    MethodSource.Instance(customerService, "Customer Service")
)
    .supportingTypes(Customer::class, Address::class)
    .build()

val customerReflectionApiConfig = reflectionApiConfig(customerReflectionConfig)
    .scalarConverters(
        scalarConverter<CustomerId> { CustomerId(it) },
        scalarConverter<EmailAddress> { EmailAddress(it) }
    )
    .build()

fun main() {
    WebServer(customerReflectionApiConfig, WebServerConfig()).start()
}