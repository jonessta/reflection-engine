package au.clef.web.demo

import au.clef.api.reflectionApiConfig
import au.clef.api.stringScalarConverter
import au.clef.app.demo.model.*
import au.clef.engine.MethodSource.Instance
import au.clef.engine.MethodSource.InstanceMethod
import au.clef.engine.MethodSource.StaticMethod
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val customerService: CustomerService = CustomerService()
private val acmeService: AcmeService = AcmeService()

internal val customerReflectionConfig = reflectionConfig(
    Instance(acmeService, "AcmeService"),
    InstanceMethod(customerService, "Customer Service", CustomerService::findCustomer),
    InstanceMethod(customerService, "Customer Service", CustomerService::normalizeEmail),
    StaticMethod(::myAddKotlinFunction),
    StaticMethod(Math::class, "min", Int::class, Int::class),
    StaticMethod(Math::class, "max", Int::class, Int::class)
)
    .supportingTypes(Customer::class, Address::class, Person::class)
    .build()

val customerReflectionApiConfig = reflectionApiConfig(customerReflectionConfig)
    .scalarConverters(
        stringScalarConverter(decodeText = ::CustomerId),
        stringScalarConverter(decodeText = ::EmailAddress)
    )
    .build()

fun main() {
    WebServer(
        apiConfig = customerReflectionApiConfig,
        webConfig = WebServerConfig()
    ).start()
}