package au.clef.api

import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import au.clef.api.model.ValueDto
import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource.InstanceMethod
import au.clef.engine.ReflectionConfig
import au.clef.engine.ReflectionEngine
import au.clef.engine.reflectionConfig
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@JvmInline
value class CustomerId(val value: String)

@JvmInline
value class EmailAddress(val value: String)

data class Address(
    val number: Int,
    val street: String,
    val zipCode: String
)

data class Customer(
    val id: CustomerId,
    val name: String,
    val email: EmailAddress1,
    val address: Address
)

class CustomerService {
    fun findCustomer(id: CustomerId): Customer =
        Customer(
            id = id,
            name = "Alice",
            email = EmailAddress1("alice@example.com"),
            address = Address(
                number = 2,
                street = "Smith St",
                zipCode = "2321"
            )
        )

    fun normalizeEmail(email: EmailAddress1): EmailAddress1 =
        EmailAddress1(email.value.trim().lowercase())
}

class InlineScalarJsonTest {

    private val customerService = CustomerService()

    private val reflectionConfig: ReflectionConfig = reflectionConfig(
        InstanceMethod(customerService, "Customer Service", CustomerService::findCustomer),
        InstanceMethod(customerService, "Customer Service", CustomerService::normalizeEmail)
    )
        .supportingTypes(Customer::class, Address::class)
        .build()

    private val scalarTypeRegistry = ScalarTypeRegistry(
        userDefinedConverters = listOf(
            scalarConverter<CustomerId>({ JsonPrimitive(it.value) }, { CustomerId(it) }),
            scalarConverter<EmailAddress1>({ JsonPrimitive(it.value) }, { EmailAddress1(it) })
        )
    )

    private val engine = ReflectionEngine(
        reflectionConfig = reflectionConfig
    )

    private val requestValueMapper = RequestValueMapper(
        classResolver = DefaultClassResolver(engine, scalarTypeRegistry),
        scalarTypeRegistry = scalarTypeRegistry
    )

    private val responseValueMapper = ResponseValueMapper(
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

        assertEquals(CustomerId::class.java, param.logicalType)
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
        val response = invoke(
            InvocationRequest(
                executionId = execution.executionId,
                args = listOf(
                    ValueDto.Scalar(JsonPrimitive("cust-123"))
                )
            )
        )

        val result = assertIs<ValueDto.Record>(response.result)

        val id = assertIs<ValueDto.Scalar>(result.fields.getValue("id"))
        val name = assertIs<ValueDto.Scalar>(result.fields.getValue("name"))
        val email = assertIs<ValueDto.Scalar>(result.fields.getValue("email"))
        val address = assertIs<ValueDto.Record>(result.fields.getValue("address"))

        assertEquals(JsonPrimitive("cust-123"), id.value)
        assertEquals(JsonPrimitive("Alice"), name.value)
        assertEquals(JsonPrimitive("alice@example.com"), email.value)

        val street = assertIs<ValueDto.Scalar>(address.fields.getValue("street"))
        assertEquals(JsonPrimitive("Smith St"), street.value)
    }

    @Test
    fun `normalizeEmail accepts and returns scalar EmailAddress`() {
        val execution: ExecutionContext.Instance =
            engine.executionContexts()
                .filterIsInstance<ExecutionContext.Instance>()
                .first { context: ExecutionContext.Instance ->
                    engine.descriptor(context.methodId).reflectedName == "normalizeEmail"
                }

        val response = invoke(
            InvocationRequest(
                executionId = execution.executionId,
                args = listOf(
                    ValueDto.Scalar(JsonPrimitive("  Alice@Example.COM "))
                )
            )
        )

        val result = assertIs<ValueDto.Scalar>(response.result)
        assertEquals(JsonPrimitive("alice@example.com"), result.value)
    }

    private fun invoke(request: InvocationRequest): InvocationResponse {
        val executionContext = engine.executionContext(request.executionId)
        val descriptor = engine.descriptor(executionContext.methodId)

        val args: List<Any?> = request.args.zip(descriptor.parameters).map { (argDto, param) ->
            requestValueMapper.materialize(argDto, param.runtimeType)
        }
        require(request.args.size == descriptor.parameters.size) {
            "Expected ${descriptor.parameters.size} args for ${descriptor.id}, got ${request.args.size}"
        }
        val result = when (executionContext) {
            is ExecutionContext.Static ->
                engine.invokeStatic(descriptor, args)

            is ExecutionContext.Instance ->
                engine.invokeInstance(descriptor, executionContext.instance, args)
        }

        return InvocationResponse(
            result = responseValueMapper.toDtoValue(result)
        )
    }
}