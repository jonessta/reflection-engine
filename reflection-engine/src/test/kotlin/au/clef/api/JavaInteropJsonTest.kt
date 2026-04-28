package au.clef.api

import au.clef.api.model.*
import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.registry.MethodSourceRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.LocalDate
import java.time.Month
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class JavaInteropJsonTest {

    private val json = Json {
        prettyPrint = true
        classDiscriminator = "kind"
    }

    private val methodSources: List<MethodSource> = listOf(
        MethodSource.StaticMethod.from(LocalDate::class, "of", Int::class, Int::class, Int::class),
        MethodSource.StaticMethod.from(URI::class, "create", String::class),
        MethodSource.StaticMethod.from(Locale::class, "forLanguageTag", String::class),
        MethodSource.StaticMethod.from(Collections::class, "singletonMap", Any::class, Any::class)
    )

    private val registry = MethodSourceRegistry(
        methodSources = methodSources,
        methodSupportingTypes = emptyList(),
        inheritanceLevel = InheritanceLevel.DeclaredOnly
    )

    private val engine = ReflectionEngine(
        reflectionRegistry = registry
    )

    private val valueMapper = ValueMapper(
        classResolver = DefaultClassResolver(engine.methodSourceTypes)
    )

    private val responseValueMapper = ResponseValueMapper()

    @Test
    fun `generate descriptors and invoke JDK methods with JSON`() {
        val descriptors = registry.allExecutionContexts().map(::toExecutionDescriptorDto)

        val localDateDescriptor =
            descriptors.first { it.reflectedName == "of" && it.returnType == "java.time.LocalDate" }
        val uriDescriptor =
            descriptors.first { it.reflectedName == "create" && it.returnType == "java.net.URI" }
        val localeDescriptor =
            descriptors.first { it.reflectedName == "forLanguageTag" }
        val singletonMapDescriptor =
            descriptors.first { it.reflectedName == "singletonMap" }

        val localDateRequest = InvocationRequest(
            executionId = localDateDescriptor.executionId,
            args = listOf(
                ValueDto.Scalar(JsonPrimitive(2026)),
                ValueDto.Scalar(JsonPrimitive(4)),
                ValueDto.Scalar(JsonPrimitive(28))
            )
        )

        val uriRequest = InvocationRequest(
            executionId = uriDescriptor.executionId,
            args = listOf(
                ValueDto.Scalar(JsonPrimitive("https://example.com/a/b?x=1"))
            )
        )

        val localeRequest = InvocationRequest(
            executionId = localeDescriptor.executionId,
            args = listOf(
                ValueDto.Scalar(JsonPrimitive("en-AU"))
            )
        )

        val singletonMapRequest = InvocationRequest(
            executionId = singletonMapDescriptor.executionId,
            args = listOf(
                ValueDto.Scalar(JsonPrimitive("key1")),
                ValueDto.Scalar(JsonPrimitive("value1"))
            )
        )

        val localDateResponse = invoke(localDateRequest)
        val uriResponse = invoke(uriRequest)
        val localeResponse = invoke(localeRequest)
        val singletonMapResponse = invoke(singletonMapRequest)

        assertScalarString(localDateResponse, "2026-04-28")
        assertScalarString(uriResponse, "https://example.com/a/b?x=1")

        val localeScalar = assertIs<ValueDto.Scalar>(localeResponse.result)
        assertEquals(JsonPrimitive("en_AU"), localeScalar.value)

        val mapResult = assertIs<ValueDto.MapValue>(singletonMapResponse.result)
        assertEquals(1, mapResult.entries.size)
    }

    @Test
    fun `invokes enum factory method from JSON`() {
        val response = invokeSingleStatic(
            methodSource = MethodSource.StaticMethod.from(Month::class, "valueOf", String::class),
            args = listOf(ValueDto.Scalar(JsonPrimitive("APRIL")))
        )

        assertScalarString(response, "APRIL")
    }

    @Test
    fun `rejects invalid enum value`() {
        val localRegistry = MethodSourceRegistry(
            listOf(MethodSource.StaticMethod.from(Month::class, "valueOf", String::class))
        )
        val localEngine = ReflectionEngine(reflectionRegistry = localRegistry)
        val localMapper = ValueMapper(DefaultClassResolver(localEngine.methodSourceTypes))
        val execution = localRegistry.allExecutionContexts().single()

        val request = InvocationRequest(
            executionId = execution.executionId,
            args = listOf(ValueDto.Scalar(JsonPrimitive("NOT_A_MONTH")))
        )

        assertFailsWith<Exception> {
            val args = request.args.map(localMapper::toEngineValue)
            when (execution) {
                is ExecutionContext.Static ->
                    localEngine.invokeStatic(execution.descriptor, args)

                is ExecutionContext.Instance ->
                    localEngine.invokeInstance(execution.descriptor, execution.instance, args)
            }
        }
    }

    @Test
    fun `invokes Java varargs method from JSON list`() {
        val response = invokeSingleStatic(
            methodSource = MethodSource.StaticMethod.from(
                java.nio.file.Paths::class,
                "get",
                String::class,
                Array<String>::class
            ),
            args = listOf(
                ValueDto.Scalar(JsonPrimitive("root")),
                ValueDto.ListValue(
                    listOf(
                        ValueDto.Scalar(JsonPrimitive("child")),
                        ValueDto.Scalar(JsonPrimitive("leaf.txt"))
                    )
                )
            )
        )

        assertScalarString(response, "root\\child\\leaf.txt")
    }

    @Test
    fun `supports maps with non string keys`() {
        val response = invokeSingleStatic(
            methodSource = MethodSource.StaticMethod.from(
                Collections::class,
                "singletonMap",
                Any::class,
                Any::class
            ),
            args = listOf(
                ValueDto.Scalar(JsonPrimitive(123)),
                ValueDto.Scalar(JsonPrimitive("value-123"))
            )
        )

        val mapValue = assertIs<ValueDto.MapValue>(response.result)
        assertEquals(1, mapValue.entries.size)

        val entry = mapValue.entries.single()
        val key = assertIs<ValueDto.Scalar>(entry.key)
        val value = assertIs<ValueDto.Scalar>(entry.value)

        assertEquals(JsonPrimitive(123), key.value)
        assertEquals(JsonPrimitive("value-123"), value.value)
    }

    private fun invoke(request: InvocationRequest): InvocationResponse {
        val executionContext = registry.executionContext(request.executionId)
        val args = request.args.map(valueMapper::toEngineValue)

        val result = when (executionContext) {
            is ExecutionContext.Static ->
                engine.invokeStatic(executionContext.descriptor, args)

            is ExecutionContext.Instance ->
                engine.invokeInstance(executionContext.descriptor, executionContext.instance, args)
        }

        return InvocationResponse(
            result = responseValueMapper.toDtoValue(result)
        )
    }

    private fun invokeSingleStatic(
        methodSource: MethodSource.StaticMethod,
        args: List<ValueDto>
    ): InvocationResponse {
        val localRegistry = MethodSourceRegistry(listOf(methodSource))
        val localEngine = ReflectionEngine(reflectionRegistry = localRegistry)
        val localValueMapper = ValueMapper(DefaultClassResolver(localEngine.methodSourceTypes))
        val localResponseMapper = ResponseValueMapper()
        val execution = localRegistry.allExecutionContexts().single() as ExecutionContext.Static

        val result = localEngine.invokeStatic(
            execution.descriptor,
            args.map(localValueMapper::toEngineValue)
        )

        return InvocationResponse(localResponseMapper.toDtoValue(result))
    }

    private fun assertScalarString(response: InvocationResponse, expected: String) {
        val scalar = assertIs<ValueDto.Scalar>(response.result)
        assertEquals(JsonPrimitive(expected), scalar.value)
    }

    private fun toExecutionDescriptorDto(executionContext: ExecutionContext): ExecutionDescriptorDto {
        val descriptor = executionContext.descriptor

        return ExecutionDescriptorDto(
            executionId = executionContext.executionId,
            instanceDescription = when (executionContext) {
                is ExecutionContext.Static -> null
                is ExecutionContext.Instance -> executionContext.instanceDescription
            },
            reflectedName = descriptor.reflectedName,
            displayName = descriptor.displayName,
            returnType = descriptor.returnType.name,
            isStatic = descriptor.isStatic,
            parameters = descriptor.parameters.map { param ->
                ParamDescriptorDto(
                    index = param.index,
                    type = param.type.name,
                    reflectedName = param.reflectedName,
                    name = param.name,
                    label = param.label,
                    nullable = param.nullable
                )
            }
        )
    }
}