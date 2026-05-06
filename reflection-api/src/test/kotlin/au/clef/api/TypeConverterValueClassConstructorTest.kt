package au.clef.api

import au.clef.api.model.ScalarValue
import au.clef.api.model.ScalarValue.NumberValue
import au.clef.api.model.ScalarValue.StringValue
import au.clef.api.model.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TypeConverterValueClassConstructorTest {

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

    private val scalarRegistry: ScalarTypeRegistry =
        ScalarTypeRegistry(
            userDefinedConverters = listOf(
                scalarConverter<CustomerId>(
                    encode = { value: CustomerId -> StringValue(value.value) },
                    decode = { value: ScalarValue ->
                        when (value) {
                            is StringValue -> CustomerId(value.value)
                            else -> throw IllegalArgumentException("Expected string scalar for CustomerId")
                        }
                    }
                ),
                scalarConverter<EmailAddress>(
                    encode = { value: EmailAddress -> StringValue(value.value) },
                    decode = { value: ScalarValue ->
                        when (value) {
                            is StringValue -> EmailAddress(value.value)
                            else -> throw IllegalArgumentException("Expected string scalar for EmailAddress")
                        }
                    }
                )
            )
        )

    private val converter = TypeConverter(scalarRegistry)

    @Test
    fun materialize_buildsKotlinDataClassWithValueClassConstructorParameters() {
        val value = Value.Record(
            type = Customer::class.java,
            fields = mapOf(
                "id" to Value.Scalar(StringValue("cust-123")),
                "email" to Value.Scalar(StringValue("alice@example.com")),
                "age" to Value.Scalar(NumberValue("41")),
                "address" to Value.Record(
                    type = Address::class.java,
                    fields = mapOf(
                        "street" to Value.Scalar(StringValue("Smith St")),
                        "zipCode" to Value.Scalar(StringValue("2321"))
                    )
                )
            )
        )

        val result: Any? = converter.materialize(value, Customer::class.java)
        val customer: Customer = assertIs(result)

        assertEquals(CustomerId("cust-123"), customer.id)
        assertEquals(EmailAddress("alice@example.com"), customer.email)
        assertEquals(41, customer.age)
        assertEquals(Address("Smith St", "2321"), customer.address)
    }

    @Test
    fun materialize_buildsNestedRecordInsideKotlinDataClassWithValueClassParameters() {
        data class Order(
            val customer: Customer,
            val note: String
        )

        val value = Value.Record(
            type = Order::class.java,
            fields = mapOf(
                "customer" to Value.Record(
                    type = Customer::class.java,
                    fields = mapOf(
                        "id" to Value.Scalar(StringValue("cust-456")),
                        "email" to Value.Scalar(StringValue("bob@example.com")),
                        "age" to Value.Scalar(NumberValue("29")),
                        "address" to Value.Record(
                            type = Address::class.java,
                            fields = mapOf(
                                "street" to Value.Scalar(StringValue("Queen St")),
                                "zipCode" to Value.Scalar(StringValue("3000"))
                            )
                        )
                    )
                ),
                "note" to Value.Scalar(StringValue("priority"))
            )
        )

        val result: Any? = converter.materialize(value, Order::class.java)
        val order: Order = assertIs(result)

        assertEquals(CustomerId("cust-456"), order.customer.id)
        assertEquals(EmailAddress("bob@example.com"), order.customer.email)
        assertEquals(29, order.customer.age)
        assertEquals(Address("Queen St", "3000"), order.customer.address)
        assertEquals("priority", order.note)
    }

    @Test
    fun materialize_doesNotFailForKotlinPrimaryConstructorWithValueClassParameters() {
        val value = Value.Record(
            type = Customer::class.java,
            fields = mapOf(
                "id" to Value.Scalar(StringValue("cust-999")),
                "email" to Value.Scalar(StringValue("test@example.com")),
                "age" to Value.Scalar(NumberValue("50")),
                "address" to Value.Record(
                    type = Address::class.java,
                    fields = mapOf(
                        "street" to Value.Scalar(StringValue("George St")),
                        "zipCode" to Value.Scalar(StringValue("2000"))
                    )
                )
            )
        )

        val customer = converter.materialize(value, Customer::class.java)
        assertIs<Customer>(customer)
    }
}