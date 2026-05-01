package au.clef.api

import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import au.clef.api.model.ValueDto
import au.clef.engine.ExecutionId
import au.clef.engine.MethodSource
import au.clef.engine.reflectionConfig
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReflectionServiceApiTest {

    private val api: ReflectionServiceApi = ReflectionServiceApi(
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
                    encode = { JsonPrimitive(it.value) },
                    decode = { EmailAddress1(it) }
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
        val greetDescriptor: ExecutionDescriptorDto =
            api.executionDescriptors().first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "greet"
            }

        assertEquals("Sample Service", greetDescriptor.instanceDescription)
        assertFalse(greetDescriptor.isStatic)
    }

    @Test
    fun executionDescriptors_hasNullInstanceDescription_forStaticMethods() {
        val sumDescriptor: ExecutionDescriptorDto =
            api.executionDescriptors().first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "sum"
            }

        assertNull(sumDescriptor.instanceDescription)
        assertTrue(sumDescriptor.isStatic)
    }

    @Test
    fun executionDescriptors_marksScalarLikeParameters() {
        val normalizeDescriptor: ExecutionDescriptorDto =
            api.executionDescriptors().first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "normalizeEmail"
            }

        assertEquals(1, normalizeDescriptor.parameters.size)

        val param = normalizeDescriptor.parameters.single()
        assertEquals(EmailAddress1::class.java.name, param.type)
        assertTrue(param.scalarLike)
    }

    @Test
    fun executionDescriptors_marksStructuredParameters_asNotScalarLike() {
        val echoRecordDescriptor: ExecutionDescriptorDto =
            api.executionDescriptors().first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "echoRecord"
            }

        val param = echoRecordDescriptor.parameters.single()
        assertEquals(SampleRecord::class.java.name, param.type)
        assertFalse(param.scalarLike)
    }

    @Test
    fun invoke_callsStaticMethod() {
        val execution: ExecutionDescriptorDto =
            api.executionDescriptors().first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "sum"
            }

        val response: InvocationResponse =
            api.invoke(
                InvocationRequest(
                    executionId = execution.executionId,
                    args = listOf(
                        ValueDto.Scalar(JsonPrimitive("2")),
                        ValueDto.Scalar(JsonPrimitive("3"))
                    )
                )
            )

        val result: ValueDto.Scalar = assertIs(response.result)
        assertEquals(JsonPrimitive(5), result.value)
    }

    @Test
    fun invoke_callsInstanceMethod() {
        val execution: ExecutionDescriptorDto =
            api.executionDescriptors().first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "greet"
            }

        val response: InvocationResponse =
            api.invoke(
                InvocationRequest(
                    executionId = execution.executionId,
                    args = listOf(
                        ValueDto.Scalar(JsonPrimitive("Alice"))
                    )
                )
            )

        val result: ValueDto.Scalar = assertIs(response.result)
        assertEquals(JsonPrimitive("Hello Alice"), result.value)
    }

    @Test
    fun invoke_handlesScalarWrapperParameterAndReturnValue() {
        val execution: ExecutionDescriptorDto =
            api.executionDescriptors().first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "normalizeEmail"
            }

        val response: InvocationResponse =
            api.invoke(
                InvocationRequest(
                    executionId = execution.executionId,
                    args = listOf(
                        ValueDto.Scalar(JsonPrimitive("  Alice@Example.COM "))
                    )
                )
            )

        val result: ValueDto.Scalar = assertIs(response.result)
        assertEquals(JsonPrimitive("alice@example.com"), result.value)
    }

    @Test
    fun invoke_handlesStructuredRecordParameterAndReturnValue() {
        val execution: ExecutionDescriptorDto =
            api.executionDescriptors().first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "echoRecord"
            }

        val response: InvocationResponse =
            api.invoke(
                InvocationRequest(
                    executionId = execution.executionId,
                    args = listOf(
                        ValueDto.Record(
                            type = SampleRecord::class.java.name,
                            fields = mapOf(
                                "name" to ValueDto.Scalar(JsonPrimitive("Bob")),
                                "age" to ValueDto.Scalar(JsonPrimitive("41"))
                            )
                        )
                    )
                )
            )

        val result: ValueDto.Record = assertIs(response.result)
        val name: ValueDto.Scalar = assertIs(result.fields.getValue("name"))
        val age: ValueDto.Scalar = assertIs(result.fields.getValue("age"))

        assertEquals(JsonPrimitive("Bob"), name.value)
        assertEquals(JsonPrimitive(41), age.value)
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
                            ValueDto.Scalar(JsonPrimitive("2"))
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
        val descriptor: ExecutionDescriptorDto =
            api.executionDescriptors().first { dto: ExecutionDescriptorDto ->
                dto.reflectedName == "greet"
            }

        assertEquals(1, descriptor.parameters.size)

        val param = descriptor.parameters.single()
        assertEquals(0, param.index)
        assertEquals(String::class.java.name, param.type)
        assertNotNull(param.reflectedName)
        assertNotNull(param.name)
        assertFalse(param.nullable)
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

    fun greet(name: String): String =
        "Hello $name"

    fun normalizeEmail(email: EmailAddress1): EmailAddress1 =
        EmailAddress1(email.value.trim().lowercase())

    fun echoRecord(record: SampleRecord): SampleRecord =
        record
}

class SampleStatics {

    companion object {
        @JvmStatic
        fun sum(a: Int, b: Int): Int =
            a + b
    }
}