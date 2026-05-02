package au.clef.api

import au.clef.api.model.InvocationRequest
import au.clef.api.model.ScalarValue
import au.clef.api.model.Value
import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource.InstanceMethod
import au.clef.engine.ReflectionConfig
import au.clef.engine.ReflectionEngine
import au.clef.engine.reflectionConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@JvmInline
value class CustomerId1(val value: String)

@JvmInline
value class EmailAddress(val value: String)

data class Address3(
    val number: Int,
    val street: String,
    val zipCode: String
)

data class Customer(
    val id: CustomerId1,
    val name: String,
    val email: EmailAddress1,
    val address: Address3
)

class CustomerService {
    fun findCustomer(id: CustomerId1): Customer =
        Customer(
            id = id,
            name = "Alice",
            email = EmailAddress1("alice@example.com"),
            address = Address3(
                number = 2,
                street = "Smith St",
                zipCode = "2321"
            )
        )

    fun normalizeEmail(email: EmailAddress1): EmailAddress1 =
        EmailAddress1(email.value.trim().lowercase())
}

class InlineScalarJsonTest {

    private val customerService: CustomerService = CustomerService()

    private val reflectionConfig: ReflectionConfig = reflectionConfig(
        InstanceMethod(
            instance = customerService,
            instanceDescription = "Customer Service",
            function = CustomerService::findCustomer
        ),
        InstanceMethod(
            instance = customerService,
            instanceDescription = "Customer Service",
            function = CustomerService::normalizeEmail
        )
    )
        .supportingTypes(Customer::class, Address3::class)
        .build()

    private val scalarTypeRegistry: ScalarTypeRegistry = ScalarTypeRegistry(
        userDefinedConverters = listOf(
            scalarConverter<CustomerId1>(
                encode = { value: CustomerId1 ->
                    ScalarValue.StringValue(value.value)
                },
                decode = { value: ScalarValue ->
                    when (value) {
                        is ScalarValue.StringValue -> CustomerId1(value.value)
                        else -> throw IllegalArgumentException("Expected string scalar for CustomerId")
                    }
                }
            ),
            scalarConverter<EmailAddress1>(
                encode = { value: EmailAddress1 ->
                    ScalarValue.StringValue(value.value)
                },
                decode = { value: ScalarValue ->
                    when (value) {
                        is ScalarValue.StringValue -> EmailAddress1(value.value)
                        else -> throw IllegalArgumentException("Expected string scalar for EmailAddress")
                    }
                }
            )
        )
    )

    private val engine: ReflectionEngine = ReflectionEngine(
        reflectionConfig = reflectionConfig
    )

    private val requestValueMapper: RequestValueMapper = RequestValueMapper(
        scalarTypeRegistry = scalarTypeRegistry
    )

    private val responseValueMapper: ResponseValueMapper = ResponseValueMapper(
        scalarRegistry = scalarTypeRegistry
    )

    @Test
    fun `customerId parameter is exposed as scalar like`() {
        val execution: ExecutionContext.Instance =
            engine.executionContexts()
                .filterIsInstance<ExecutionContext.Instance>()
                .first { context: ExecutionContext.Instance ->
                    engine.descriptor(context.methodId).reflectedName == "findCustomer"
                }

        val descriptor = engine.descriptor(execution.methodId)
        val param = descriptor.parameters.single()

        assertEquals(CustomerId1::class.java, param.logicalType)
        assertTrue(requestValueMapper.isScalarLike(param.logicalType))
    }

    @Test
    fun `findCustomer accepts scalar CustomerId and returns nested scalar wrappers`() {
        val execution: ExecutionContext.Instance =
            engine.executionContexts()
                .filterIsInstance<ExecutionContext.Instance>()
                .first { context: ExecutionContext.Instance ->
                    engine.descriptor(context.methodId).reflectedName == "findCustomer"
                }

        val response: Value = invoke(
            InvocationRequest(
                executionId = execution.executionId,
                args = listOf(
                    Value.Scalar(ScalarValue.StringValue("cust-123"))
                )
            )
        )

        val result: Value.Record = assertIs(response)

        val id: Value.Scalar = assertIs(result.fields.getValue("id"))
        val name: Value.Scalar = assertIs(result.fields.getValue("name"))
        val email: Value.Scalar = assertIs(result.fields.getValue("email"))
        val address: Value.Record = assertIs(result.fields.getValue("address"))

        assertEquals(ScalarValue.StringValue("cust-123"), id.value)
        assertEquals(ScalarValue.StringValue("Alice"), name.value)
        assertEquals(ScalarValue.StringValue("alice@example.com"), email.value)

        val street: Value.Scalar = assertIs(address.fields.getValue("street"))
        assertEquals(ScalarValue.StringValue("Smith St"), street.value)
    }

    @Test
    fun `normalizeEmail accepts and returns scalar EmailAddress`() {
        val execution: ExecutionContext.Instance =
            engine.executionContexts()
                .filterIsInstance<ExecutionContext.Instance>()
                .first { context: ExecutionContext.Instance ->
                    engine.descriptor(context.methodId).reflectedName == "normalizeEmail"
                }

        val response: Value = invoke(
            InvocationRequest(
                executionId = execution.executionId,
                args = listOf(
                    Value.Scalar(ScalarValue.StringValue("  Alice@Example.COM "))
                )
            )
        )

        val result: Value.Scalar = assertIs(response)
        assertEquals(ScalarValue.StringValue("alice@example.com"), result.value)
    }

    private fun invoke(request: InvocationRequest): Value {
        val executionContext: ExecutionContext = engine.executionContext(request.executionId)
        val descriptor = engine.descriptor(executionContext.methodId)

        require(request.args.size == descriptor.parameters.size) {
            "Expected ${descriptor.parameters.size} args for ${descriptor.id}, got ${request.args.size}"
        }

        val args: List<Any?> =
            request.args.zip(descriptor.parameters).map { (argValue, param) ->
                requestValueMapper.materialize(argValue, param.runtimeType)
            }

        val result: Any? =
            when (executionContext) {
                is ExecutionContext.Static ->
                    engine.invokeStatic(descriptor, args)

                is ExecutionContext.Instance ->
                    engine.invokeInstance(descriptor, executionContext.instance, args)
            }

        return responseValueMapper.toValue(result)
    }
}