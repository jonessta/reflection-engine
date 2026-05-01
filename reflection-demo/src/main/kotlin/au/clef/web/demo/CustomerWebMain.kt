package au.clef.web.demo

import au.clef.api.reflectionApiConfig
import au.clef.api.scalarConverter
import au.clef.app.demo.model.*
import au.clef.engine.MethodSource
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig
import kotlinx.serialization.json.JsonPrimitive

private val customerService = CustomerService()

internal val customerReflectionConfig = reflectionConfig(
    MethodSource.InstanceMethod(customerService, "Customer Service", CustomerService::findCustomer),
    MethodSource.InstanceMethod(customerService, "Customer Service", CustomerService::normalizeEmail)
//    MethodSource.Instance(customerService, "Customer Service")
)
    .supportingTypes(Customer::class, Address::class)
    .build()

val customerReflectionApiConfig = reflectionApiConfig(customerReflectionConfig)
    .scalarConverters(
        scalarConverter<CustomerId>(
            encode = { value: CustomerId -> JsonPrimitive(value.value) },
            decode = { text: String -> CustomerId(text) }
        ),
        scalarConverter<EmailAddress>(
            encode = { value: EmailAddress -> JsonPrimitive(value.value) },
            decode = { text: String -> EmailAddress(text) }
        )
    )
    .build()

fun main() {
    WebServer(customerReflectionApiConfig, WebServerConfig()).start()
}