package au.clef.api

import au.clef.api.model.ScalarValue
import au.clef.api.model.Value
import au.clef.engine.MethodSource
import au.clef.engine.reflectionConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReflectionServiceApiValueClassIntegrationTest {

    @JvmInline
    value class CustomerId(val value: String)

    @JvmInline
    value class EmailAddress(val value: String)

    data class Address(
        val street: String,
        val zipCode: String
    )

    data class Customer(
        val id: CustomerId,
        val email: EmailAddress,
        val age: Int,
        val address: Address
    )

    class CustomerService {

        fun customerLabel(customer: Customer): String =
            "${customer.id.value}:${customer.email.value}:${customer.age}:${customer.address.street}:${customer.address.zipCode}"
    }

    private val customerService = CustomerService()

    private val apiConfig: ReflectionApiConfig =
        reflectionApiConfig(
            reflectionConfig(
                MethodSource.InstanceMethod(
                    instance = customerService,
                    sourceDescription = "Customer Service",
                    function = CustomerService::customerLabel
                )
            )
                .supportingTypes(Customer::class, Address::class)
                .build()
        )
            .scalarConverters(
                scalarConverter<CustomerId>(
                    encode = { value: CustomerId ->
                        ScalarValue.StringValue(value.value)
                    },
                    decode = { value: ScalarValue ->
                        when (value) {
                            is ScalarValue.StringValue -> CustomerId(value.value)
                            else -> throw IllegalArgumentException("Expected string scalar for CustomerId")
                        }
                    }
                ),
                scalarConverter<EmailAddress>(
                    encode = { value: EmailAddress ->
                        ScalarValue.StringValue(value.value)
                    },
                    decode = { value: ScalarValue ->
                        when (value) {
                            is ScalarValue.StringValue -> EmailAddress(value.value)
                            else -> throw IllegalArgumentException("Expected string scalar for EmailAddress")
                        }
                    }
                )
            )
            .build()

    private val api = ReflectionServiceApi(apiConfig)

    @Test
    fun invoke_materializesNestedRecordWithValueClassConstructorParameters() {
        val execution = api.executionDescriptors().single()

        val request = au.clef.api.model.InvocationRequest(
            executionId = execution.executionId,
            args = listOf(
                Value.Record(
                    type = Customer::class.java,
                    fields = mapOf(
                        "id" to Value.Scalar(ScalarValue.StringValue("cust-123")),
                        "email" to Value.Scalar(ScalarValue.StringValue("alice@example.com")),
                        "age" to Value.Scalar(ScalarValue.NumberValue("41")),
                        "address" to Value.Record(
                            type = Address::class.java,
                            fields = mapOf(
                                "street" to Value.Scalar(ScalarValue.StringValue("Smith St")),
                                "zipCode" to Value.Scalar(ScalarValue.StringValue("2321"))
                            )
                        )
                    )
                )
            )
        )

        val response: Value = api.invoke(request)
        val scalar: Value.Scalar = assertIs(response)
        val result: ScalarValue.StringValue = assertIs(scalar.value)

        assertEquals(
            "cust-123:alice@example.com:41:Smith St:2321",
            result.value
        )
    }

    @Test
    fun invoke_roundTripsJsonRequestBodyForNestedRecordWithValueClassParameters() {
        val execution = api.executionDescriptors().single()

        val json: Json = Json {
            serializersModule = api.jsonSerializersModule
            prettyPrint = false
            ignoreUnknownKeys = false
        }

        val requestJson = """
            {
              "executionId": "${execution.executionId}",
              "args": [
                {
                  "kind": "record",
                  "type": "${Customer::class.java.name}",
                  "fields": {
                    "id": { "kind": "scalar", "value": "cust-456" },
                    "email": { "kind": "scalar", "value": "bob@example.com" },
                    "age": { "kind": "scalar", "value": 29 },
                    "address": {
                      "kind": "record",
                      "type": "${Address::class.java.name}",
                      "fields": {
                        "street": { "kind": "scalar", "value": "Queen St" },
                        "zipCode": { "kind": "scalar", "value": "3000" }
                      }
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val request = json.decodeFromString(
            au.clef.api.model.InvocationRequest.serializer(),
            requestJson
        )

        val response: Value = api.invoke(request)
        val scalar: Value.Scalar = assertIs(response)
        val result: ScalarValue.StringValue = assertIs(scalar.value)

        assertEquals(
            "cust-456:bob@example.com:29:Queen St:3000",
            result.value
        )
    }
}