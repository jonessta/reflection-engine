package au.clef.api

import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import au.clef.api.model.ParamDescriptorDto
import au.clef.api.model.ValueDto
import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource.StaticMethod
import au.clef.engine.ReflectionConfig
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.reflectionConfig
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Paths
import java.time.LocalDate
import java.time.Month
import java.util.Collections
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class JavaInteropJsonTest {

    private val reflectionConfig: ReflectionConfig = reflectionConfig(
        StaticMethod(LocalDate::class, "of", Int::class, Int::class, Int::class),
        StaticMethod(URI::class, "create", String::class),
        StaticMethod(Locale::class, "forLanguageTag", String::class),
        StaticMethod(Collections::class, "singletonMap", Any::class, Any::class)
    )
        .inheritanceLevel(InheritanceLevel.DeclaredOnly)
        .build()

    private val scalarTypeRegistry = ScalarTypeRegistry()

    private val engine = ReflectionEngine(
        reflectionConfig = reflectionConfig
    )

    private val requestValueMapper = RequestValueMapper(
        classResolver = DefaultClassResolver(engine, scalarTypeRegistry),
        scalarTypeRegistry = scalarTypeRegistry
    )

    private val responseValueMapper = ResponseValueMapper(
        scalarTypeRegistry = scalarTypeRegistry
    )

    @Test
    fun `generate descriptors and invoke JDK methods with JSON`() {
        val descriptors = executionDescriptors()

        val localDateDescriptor =
            descriptors.first { it.reflectedName == "of" && it.returnType == "java.time.LocalDate" }
        val uriDescriptor =
            descriptors.first { it.reflectedName == "create" && it.returnType == "java.net.URI" }
        val localeDescriptor =
            descriptors.first { it.reflectedName == "forLanguageTag" }
        val singletonMapDescriptor =
            descriptors.first { it.reflectedName == "singletonMap" }

        val localDateResponse = invoke(
            InvocationRequest(
                executionId = localDateDescriptor.executionId,
                args = listOf(
                    ValueDto.Scalar(JsonPrimitive(2026)),
                    ValueDto.Scalar(JsonPrimitive(4)),
                    ValueDto.Scalar(JsonPrimitive(28))
                )
            )
        )

        val uriResponse = invoke(
            InvocationRequest(
                executionId = uriDescriptor.executionId,
                args = listOf(
                    ValueDto.Scalar(JsonPrimitive("https://example.com/a/b?x=1"))
                )
            )
        )

        val localeResponse = invoke(
            InvocationRequest(
                executionId = localeDescriptor.executionId,
                args = listOf(
                    ValueDto.Scalar(JsonPrimitive("en-AU"))
                )
            )
        )

        val singletonMapResponse = invoke(
            InvocationRequest(
                executionId = singletonMapDescriptor.executionId,
                args = listOf(
                    ValueDto.Scalar(JsonPrimitive("key1")),
                    ValueDto.Scalar(JsonPrimitive("value1"))
                )
            )
        )

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
            methodSource = StaticMethod(Month::class, "valueOf", String::class),
            args = listOf(ValueDto.Scalar(JsonPrimitive("APRIL")))
        )

        assertScalarString(response, "APRIL")
    }

    @Test
    fun `rejects invalid enum value`() {
        val localConfig = reflectionConfig(
            StaticMethod(Month::class, "valueOf", String::class)
        ).build()

        val localScalarTypeRegistry = ScalarTypeRegistry()

        val localEngine = ReflectionEngine(reflectionConfig = localConfig)

        val localRequestValueMapper = RequestValueMapper(
            classResolver = DefaultClassResolver(localEngine, localScalarTypeRegistry),
            scalarTypeRegistry = localScalarTypeRegistry
        )

        val execution = localEngine.executionContexts().single() as ExecutionContext.Static
        val descriptor = localEngine.descriptor(execution.methodId)

        val request = InvocationRequest(
            executionId = execution.executionId,
            args = listOf(ValueDto.Scalar(JsonPrimitive("NOT_A_MONTH")))
        )

        assertFailsWith<Exception> {
            val args = request.args.zip(descriptor.parameters).map { (argDto, param) ->
                localRequestValueMapper.materialize(argDto, param.runtimeType)
            }
            localEngine.invokeStatic(descriptor, args)
        }
    }

    @Test
    fun `invokes Java varargs method from JSON list`() {
        val response = invokeSingleStatic(
            methodSource = StaticMethod(
                Paths::class,
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

        assertScalarString(response, Paths.get("root", "child", "leaf.txt").toString())
    }

    @Test
    fun `supports maps with non string keys`() {
        val response = invokeSingleStatic(
            methodSource = StaticMethod(
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
        val executionContext = engine.executionContext(request.executionId)
        val descriptor = engine.descriptor(executionContext.methodId)

        val args: List<Any?> = request.args.zip(descriptor.parameters).map { (argDto, param) ->
            requestValueMapper.materialize(argDto, param.runtimeType)
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

    private fun invokeSingleStatic(
        methodSource: StaticMethod,
        args: List<ValueDto>
    ): InvocationResponse {
        val localConfig = reflectionConfig(methodSource).build()
        val localScalarTypeRegistry = ScalarTypeRegistry()

        val localEngine = ReflectionEngine(
            reflectionConfig = localConfig
        )

        val localRequestValueMapper = RequestValueMapper(
            classResolver = DefaultClassResolver(localEngine, localScalarTypeRegistry),
            scalarTypeRegistry = localScalarTypeRegistry
        )

        val localResponseValueMapper = ResponseValueMapper(
            scalarTypeRegistry = localScalarTypeRegistry
        )

        val execution = localEngine.executionContexts().single() as ExecutionContext.Static
        val descriptor = localEngine.descriptor(execution.methodId)

        val materializedArgs: List<Any?> = args.zip(descriptor.parameters).map { (argDto, param) ->
            localRequestValueMapper.materialize(argDto, param.runtimeType)
        }

        val result = localEngine.invokeStatic(descriptor, materializedArgs)

        return InvocationResponse(localResponseValueMapper.toDtoValue(result))
    }

    private fun executionDescriptors(): List<ExecutionDescriptorDto> =
        engine.executionContexts().map { executionContext ->
            val descriptor = engine.descriptor(executionContext.methodId)
            toExecutionDescriptorDto(executionContext, descriptor)
        }

    private fun assertScalarString(response: InvocationResponse, expected: String) {
        val scalar = assertIs<ValueDto.Scalar>(response.result)
        assertEquals(JsonPrimitive(expected), scalar.value)
    }

    private fun toExecutionDescriptorDto(
        executionContext: ExecutionContext,
        descriptor: MethodDescriptor
    ): ExecutionDescriptorDto =
        ExecutionDescriptorDto(
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
                    type = param.logicalType.name,
                    reflectedName = param.reflectedName,
                    name = param.name,
                    label = param.label,
                    nullable = param.nullable,
                    scalarLike = requestValueMapper.isScalarLike(param.logicalType)
                )
            }
        )
}