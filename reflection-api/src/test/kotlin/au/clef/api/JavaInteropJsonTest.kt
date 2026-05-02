package au.clef.api

import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.ParamDescriptorDto
import au.clef.api.model.ScalarValue
import au.clef.api.model.Value
import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource.StaticMethod
import au.clef.engine.ReflectionConfig
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.reflectionConfig
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

    private val scalarTypeRegistry: ScalarTypeRegistry = ScalarTypeRegistry()

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
    fun `generate descriptors and invoke JDK methods with JSON`() {
        val descriptors: List<ExecutionDescriptorDto> = executionDescriptors()

        val localDateDescriptor: ExecutionDescriptorDto =
            descriptors.first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "of" &&
                        descriptor.returnType == "java.time.LocalDate"
            }

        val uriDescriptor: ExecutionDescriptorDto =
            descriptors.first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "create" &&
                        descriptor.returnType == "java.net.URI"
            }

        val localeDescriptor: ExecutionDescriptorDto =
            descriptors.first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "forLanguageTag"
            }

        val singletonMapDescriptor: ExecutionDescriptorDto =
            descriptors.first { descriptor: ExecutionDescriptorDto ->
                descriptor.reflectedName == "singletonMap"
            }

        val localDateResponse: Value = invoke(
            InvocationRequest(
                executionId = localDateDescriptor.executionId,
                args = listOf(
                    Value.Scalar(ScalarValue.NumberValue("2026")),
                    Value.Scalar(ScalarValue.NumberValue("4")),
                    Value.Scalar(ScalarValue.NumberValue("28"))
                )
            )
        )

        val uriResponse: Value = invoke(
            InvocationRequest(
                executionId = uriDescriptor.executionId,
                args = listOf(
                    Value.Scalar(ScalarValue.StringValue("https://example.com/a/b?x=1"))
                )
            )
        )

        val localeResponse: Value = invoke(
            InvocationRequest(
                executionId = localeDescriptor.executionId,
                args = listOf(
                    Value.Scalar(ScalarValue.StringValue("en-AU"))
                )
            )
        )

        val singletonMapResponse: Value = invoke(
            InvocationRequest(
                executionId = singletonMapDescriptor.executionId,
                args = listOf(
                    Value.Scalar(ScalarValue.StringValue("key1")),
                    Value.Scalar(ScalarValue.StringValue("value1"))
                )
            )
        )

        assertScalarString(localDateResponse, "2026-04-28")
        assertScalarString(uriResponse, "https://example.com/a/b?x=1")

        val localeScalar: Value.Scalar = assertIs(localeResponse)
        assertEquals(ScalarValue.StringValue("en-AU"), localeScalar.value)

        val mapResult: Value.MapValue = assertIs(singletonMapResponse)
        assertEquals(1, mapResult.entries.size)
    }

    @Test
    fun `invokes enum factory method from JSON`() {
        val response: Value = invokeSingleStatic(
            methodSource = StaticMethod(Month::class, "valueOf", String::class),
            args = listOf(
                Value.Scalar(ScalarValue.StringValue("APRIL"))
            )
        )

        assertScalarString(response, "APRIL")
    }

    @Test
    fun `rejects invalid enum value`() {
        val localConfig: ReflectionConfig = reflectionConfig(
            StaticMethod(Month::class, "valueOf", String::class)
        ).build()

        val localScalarTypeRegistry: ScalarTypeRegistry = ScalarTypeRegistry()

        val localEngine: ReflectionEngine = ReflectionEngine(
            reflectionConfig = localConfig
        )

        val localRequestValueMapper: RequestValueMapper = RequestValueMapper(
            scalarTypeRegistry = localScalarTypeRegistry
        )

        val execution: ExecutionContext.Static =
            localEngine.executionContexts().single() as ExecutionContext.Static

        val descriptor: MethodDescriptor = localEngine.descriptor(execution.methodId)

        val request = InvocationRequest(
            executionId = execution.executionId,
            args = listOf(
                Value.Scalar(ScalarValue.StringValue("NOT_A_MONTH"))
            )
        )

        assertFailsWith<Exception> {
            val args: List<Any?> =
                request.args.zip(descriptor.parameters).map { (argValue, param) ->
                    localRequestValueMapper.materialize(argValue, param.runtimeType)
                }

            localEngine.invokeStatic(descriptor, args)
        }
    }

    @Test
    fun `invokes Java varargs method from JSON list`() {
        val response: Value = invokeSingleStatic(
            methodSource = StaticMethod(
                Paths::class,
                "get",
                String::class,
                Array<String>::class
            ),
            args = listOf(
                Value.Scalar(ScalarValue.StringValue("root")),
                Value.ListValue(
                    listOf(
                        Value.Scalar(ScalarValue.StringValue("child")),
                        Value.Scalar(ScalarValue.StringValue("leaf.txt"))
                    )
                )
            )
        )

        assertScalarString(response, Paths.get("root", "child", "leaf.txt").toString())
    }

    @Test
    fun `supports maps with non string keys`() {
        val response: Value = invokeSingleStatic(
            methodSource = StaticMethod(
                Collections::class,
                "singletonMap",
                Any::class,
                Any::class
            ),
            args = listOf(
                Value.Scalar(ScalarValue.NumberValue("123")),
                Value.Scalar(ScalarValue.StringValue("value-123"))
            )
        )

        val mapValue: Value.MapValue = assertIs(response)
        assertEquals(1, mapValue.entries.size)

        val entry = mapValue.entries.single()
        val key: Value.Scalar = assertIs(entry.key)
        val value: Value.Scalar = assertIs(entry.value)

        assertEquals(ScalarValue.NumberValue("123"), key.value)
        assertEquals(ScalarValue.StringValue("value-123"), value.value)
    }

    private fun invoke(request: InvocationRequest): Value {
        val executionContext: ExecutionContext = engine.executionContext(request.executionId)
        val descriptor: MethodDescriptor = engine.descriptor(executionContext.methodId)

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

    private fun invokeSingleStatic(
        methodSource: StaticMethod,
        args: List<Value>
    ): Value {
        val localConfig: ReflectionConfig = reflectionConfig(methodSource).build()
        val localScalarTypeRegistry: ScalarTypeRegistry = ScalarTypeRegistry()

        val localEngine: ReflectionEngine = ReflectionEngine(
            reflectionConfig = localConfig
        )

        val localRequestValueMapper: RequestValueMapper = RequestValueMapper(
            scalarTypeRegistry = localScalarTypeRegistry
        )

        val localResponseValueMapper: ResponseValueMapper = ResponseValueMapper(
            scalarRegistry = localScalarTypeRegistry
        )

        val execution: ExecutionContext.Static =
            localEngine.executionContexts().single() as ExecutionContext.Static

        val descriptor: MethodDescriptor = localEngine.descriptor(execution.methodId)

        require(args.size == descriptor.parameters.size) {
            "Expected ${descriptor.parameters.size} args for ${descriptor.id}, got ${args.size}"
        }

        val materializedArgs: List<Any?> =
            args.zip(descriptor.parameters).map { (argValue, param) ->
                localRequestValueMapper.materialize(argValue, param.runtimeType)
            }

        val result: Any? = localEngine.invokeStatic(descriptor, materializedArgs)

        return localResponseValueMapper.toValue(result)
    }

    private fun executionDescriptors(): List<ExecutionDescriptorDto> =
        engine.executionContexts().map { executionContext: ExecutionContext ->
            val descriptor: MethodDescriptor = engine.descriptor(executionContext.methodId)
            toExecutionDescriptorDto(executionContext, descriptor)
        }

    private fun assertScalarString(response: Value, expected: String) {
        val scalar: Value.Scalar = assertIs(response)
        assertEquals(ScalarValue.StringValue(expected), scalar.value)
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
                    nullable = param.nullable,
                    scalarLike = requestValueMapper.isScalarLike(param.logicalType)
                )
            }
        )
}