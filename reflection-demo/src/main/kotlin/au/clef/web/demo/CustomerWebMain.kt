package au.clef.web.demo

import au.clef.api.reflectionApiConfig
import au.clef.api.stringScalarConverter
import au.clef.app.demo.model.CustomerId
import au.clef.app.demo.model.CustomerService
import au.clef.app.demo.model.EmailAddress
import au.clef.app.demo.model.ZipCode
import au.clef.engine.MethodSource.Instance
import au.clef.engine.reflectionConfig
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val customerService: CustomerService = CustomerService()

internal val reflectionConfig = reflectionConfig(
    Instance(customerService, "Customer Service"),
)
    .build()

val apiConfig = reflectionApiConfig(reflectionConfig)
    .scalarConverters(
        stringScalarConverter(decodeText = ::CustomerId),
        stringScalarConverter(decodeText = ::ZipCode),
        stringScalarConverter(decodeText = ::EmailAddress)
    )
    .metadataResourcePath("/config/method-metadata.json")
    .build()

fun main() {
    WebServer(apiConfig, WebServerConfig()).start()
}