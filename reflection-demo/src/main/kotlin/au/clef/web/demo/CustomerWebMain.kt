package au.clef.web.demo

import au.clef.api.ReflectionApiConfig
import au.clef.api.scalarConverter
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Customer
import au.clef.app.demo.model.CustomerId
import au.clef.app.demo.model.CustomerService
import au.clef.app.demo.model.EmailAddress
import au.clef.engine.MethodSource.InstanceMethod
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig
import kotlinx.serialization.json.JsonPrimitive

private val customerService = CustomerService()

internal val customerReflectionConfig = reflectionConfig(
    InstanceMethod(customerService, "Customer Service", "findCustomer", CustomerId::class),
    InstanceMethod(customerService, "Customer Service", "normalizeEmail", EmailAddress::class)
)
    .supportingTypes(Customer::class, Address::class)
    .build()

val customerReflectionApiConfig = ReflectionApiConfig(
    reflectionConfig = customerReflectionConfig,
    userDefinedScalarConverters = listOf(
        scalarConverter(
            type = CustomerId::class,
            encode = { JsonPrimitive(it.value) },
            decode = { CustomerId(it) }
        ),
        scalarConverter(
            type = EmailAddress::class,
            encode = { JsonPrimitive(it.value) },
            decode = { EmailAddress(it) }
        )
    )
)

val customerWebConfig = WebServerConfig(
    port = 8080,
    host = "0.0.0.0"
)

fun main() {
    WebServer(customerReflectionApiConfig, customerWebConfig).start()
}