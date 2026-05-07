package au.clef.api

import au.clef.api.model.*
import au.clef.engine.MethodSource
import au.clef.engine.model.ExecutionId
import au.clef.engine.reflectionConfig
import kotlin.test.*

class ReflectionServiceApiTest {

    private val api = ReflectionServiceApi(
        reflectionApiConfig(
            reflectionConfig(
                MethodSource.StaticClass(SampleStatics::class),
                MethodSource.Instance(SampleService(), "Sample Service")
            )
                .supportingTypes(SampleRecord::class)
                .build()
        )
            .scalarConverters(
                scalarConverter<EmailAddress1>(
                    encode = { value: EmailAddress1 -> ScalarValue.StringValue(value.value) },
                    decode = { value: ScalarValue ->
                        when (value) {
                            is ScalarValue.StringValue -> EmailAddress1(value.value)
                            else -> throw IllegalArgumentException("Expected string scalar for EmailAddress")
                        }
                    }
                )
            )
            .build()
    )

    @Test
    fun executionDescriptors_returnsStaticAndInstanceDescriptors() {
        val descriptors: List<ExecutionDescriptorDto> = api.executionDescriptors()

        assertTrue(descriptors.any { descriptor: ExecutionDescriptorDto ->
            descriptor.reflectedName == "sum" && descriptor.isStatic
        })

        assertTrue(descriptors.any { descriptor: ExecutionDescriptorDto ->
            descriptor.reflectedName == "greet" && !descriptor.isStatic
        })
    }

    @Test
    fun executionDescriptors_exposesInstanceDescription_forInstanceMethods() {
        val greetDescriptor: ExecutionDescriptorDto = api.executionDescriptors()
            .first { descriptor: ExecutionDescriptorDto -> descriptor.reflectedName == "greet" }

        assertEquals("Sample Service", greetDescriptor.sourceDescription)
        assertFalse(greetDescriptor.isStatic)
    }

    @Test
    fun executionDescriptors_hasNullInstanceDescription_forStaticMethods() {
        val sumDescriptor: ExecutionDescriptorDto = api.executionDescriptors()
            .first { descriptor: ExecutionDescriptorDto -> descriptor.reflectedName == "sum" }

        assertNull(sumDescriptor.sourceDescription)
        assertTrue(sumDescriptor.isStatic)
    }

    @Test
    fun executionDescriptors_marksScalarParameters_asScalarKind() {
        val normalizeDescriptor: ExecutionDescriptorDto = api.executionDescriptors()
            .first { descriptor: ExecutionDescriptorDto -> descriptor.reflectedName == "normalizeEmail" }

        assertEquals(1, normalizeDescriptor.parameters.size)

        val param = normalizeDescriptor.parameters.single()
        assertEquals(EmailAddress1::class.java.name, param.type)
        assertEquals(FieldKindDto.SCALAR, param.kind)
        assertTrue(param.children.isEmpty())
    }

    @Test
    fun executionDescriptors_marksStructuredParameters_asRecordKind() {
        val echoRecordDescriptor: ExecutionDescriptorDto = api.executionDescriptors()
            .first { descriptor: ExecutionDescriptorDto -> descriptor.reflectedName == "echoRecord" }

        assertEquals(1, echoRecordDescriptor.parameters.size)

        val param = echoRecordDescriptor.parameters.single()
        assertEquals(SampleRecord::class.java.name, param.type)
        assertEquals(FieldKindDto.RECORD, param.kind)
    }

    @Test
    fun invoke_callsStaticMethod() {
        val execution: ExecutionDescriptorDto = api.executionDescriptors()
            .first { descriptor: ExecutionDescriptorDto -> descriptor.reflectedName == "sum" }

        val response: Value =
            api.invoke(
                InvocationRequest(
                    executionId = execution.executionId,
                    args = listOf(
                        Value.Scalar(ScalarValue.NumberValue("2")),
                        Value.Scalar(ScalarValue.NumberValue("3"))
                    )
                )
            )

        val result: Value.Scalar = assertIs(response)
        assertEquals(ScalarValue.NumberValue("5"), result.value)
    }

    @Test
    fun invoke_callsInstanceMethod() {
        val execution: ExecutionDescriptorDto = api.executionDescriptors()
            .first { descriptor: ExecutionDescriptorDto -> descriptor.reflectedName == "greet" }

        val response: Value =
            api.invoke(
                InvocationRequest(
                    executionId = execution.executionId,
                    args = listOf(Value.Scalar(ScalarValue.StringValue("Alice")))
                )
            )

        val result: Value.Scalar = assertIs(response)
        assertEquals(ScalarValue.StringValue("Hello Alice"), result.value)
    }

    @Test
    fun invoke_handlesScalarWrapperParameterAndReturnValue() {
        val execution: ExecutionDescriptorDto = api.executionDescriptors()
            .first { descriptor: ExecutionDescriptorDto -> descriptor.reflectedName == "normalizeEmail" }

        val response: Value =
            api.invoke(
                InvocationRequest(
                    executionId = execution.executionId,
                    args = listOf(Value.Scalar(ScalarValue.StringValue("  Alice@Example.COM ")))
                )
            )

        val result: Value.Scalar = assertIs(response)
        assertEquals(ScalarValue.StringValue("alice@example.com"), result.value)
    }

    @Test
    fun invoke_handlesStructuredRecordParameterAndReturnValue() {
        val execution: ExecutionDescriptorDto = api.executionDescriptors()
            .first { descriptor: ExecutionDescriptorDto -> descriptor.reflectedName == "echoRecord" }

        val response: Value =
            api.invoke(
                InvocationRequest(
                    executionId = execution.executionId,
                    args = listOf(
                        Value.Record(
                            type = SampleRecord::class.java,
                            fields = mapOf(
                                "name" to Value.Scalar(ScalarValue.StringValue("Bob")),
                                "age" to Value.Scalar(ScalarValue.NumberValue("41"))
                            )
                        )
                    )
                )
            )

        val result: Value.Record = assertIs(response)
        val name: Value.Scalar = assertIs(result.fields.getValue("name"))
        val age: Value.Scalar = assertIs(result.fields.getValue("age"))

        assertEquals(ScalarValue.StringValue("Bob"), name.value)
        assertEquals(ScalarValue.NumberValue("41"), age.value)
    }

    @Test
    fun invoke_rejectsWrongArgumentCount() {
        val execution: ExecutionDescriptorDto =
            api.executionDescriptors().first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "sum"
            }

        val ex: IllegalArgumentException =
            assertFailsWith {
                api.invoke(
                    InvocationRequest(
                        executionId = execution.executionId,
                        args = listOf(
                            Value.Scalar(ScalarValue.NumberValue("2"))
                        )
                    )
                )
            }

        assertTrue(ex.message!!.contains("Expected 2 args"))
    }

    @Test
    fun invoke_rejectsUnknownExecutionId() {
        val ex: IllegalArgumentException =
            assertFailsWith {
                api.invoke(
                    InvocationRequest(
                        executionId = ExecutionId("missing-execution-id"),
                        args = emptyList()
                    )
                )
            }

        assertTrue(ex.message!!.contains("Unknown ID"))
    }

    @Test
    fun executionDescriptors_includeParameterMetadataShape() {
        val descriptor: ExecutionDescriptorDto = api.executionDescriptors()
            .first { dto: ExecutionDescriptorDto -> dto.reflectedName == "greet" }

        assertEquals(1, descriptor.parameters.size)

        val param = descriptor.parameters.single()
        assertEquals(0, param.index)
        assertEquals(String::class.java.name, param.type)
        assertNotNull(param.reflectedName)
        assertNotNull(param.name)
        assertFalse(param.nullable)
        assertEquals(FieldKindDto.SCALAR, param.kind)
        assertTrue(param.children.isEmpty())
    }
}

@JvmInline
value class EmailAddress1(
    val value: String
)

data class SampleRecord(
    val name: String,
    val age: Int
)

class SampleService {

    fun greet(name: String): String = "Hello $name"

    fun normalizeEmail(email: EmailAddress1): EmailAddress1 =
        EmailAddress1(email.value.trim().lowercase())

    fun echoRecord(record: SampleRecord): SampleRecord = record
}

class SampleStatics {
    companion object {

        @JvmStatic
        fun sum(a: Int, b: Int): Int = a + b
    }
}