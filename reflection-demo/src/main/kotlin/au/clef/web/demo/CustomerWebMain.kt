package au.clef.web.demo

import au.clef.api.model.ScalarValue
import au.clef.api.reflectionApiConfig
import au.clef.api.scalarConverter
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Customer
import au.clef.app.demo.model.CustomerId
import au.clef.app.demo.model.CustomerService
import au.clef.app.demo.model.EmailAddress
import au.clef.engine.MethodSource
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val customerService: CustomerService = CustomerService()

internal val customerReflectionConfig = reflectionConfig(
    MethodSource.InstanceMethod(
        instance = customerService,
        instanceDescription = "Customer Service",
        function = CustomerService::findCustomer
    ),
    MethodSource.InstanceMethod(
        instance = customerService,
        instanceDescription = "Customer Service",
        function = CustomerService::normalizeEmail
    )
)
    .supportingTypes(Customer::class, Address::class)
    .build()

val customerReflectionApiConfig = reflectionApiConfig(customerReflectionConfig)
    .scalarConverters(
        scalarConverter<CustomerId>(
            encode = { value: CustomerId -> ScalarValue.StringValue(value.value) },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> CustomerId(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar for CustomerId")
                }
            }
        ),
        scalarConverter<EmailAddress>(
            encode = { value: EmailAddress -> ScalarValue.StringValue(value.value) },
            decode = { value: ScalarValue ->
                when (value) {
                    is ScalarValue.StringValue -> EmailAddress(value.value)
                    else -> throw IllegalArgumentException("Expected string scalar for EmailAddress")
                }
            }
        )
    )
    .build()

fun main() {
    WebServer(
        apiConfig = customerReflectionApiConfig,
        webConfig = WebServerConfig()
    ).start()
}