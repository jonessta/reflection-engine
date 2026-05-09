package au.clef.api

import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.FieldDescriptorDto
import au.clef.api.model.FieldKindDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.ScalarValue
import au.clef.api.model.ScalarValue.StringValue
import au.clef.api.model.Value
import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource.StaticMethod
import au.clef.engine.ReflectionConfig
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.ParamDescriptor
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    private val engine: ReflectionEngine = ReflectionEngine(reflectionConfig)

    private val requestValueMapper: RequestValueMapper = RequestValueMapper(scalarTypeRegistry)

    private val responseValueMapper: ResponseValueMapper = ResponseValueMapper(scalarTypeRegistry)

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

        assertEquals(3, localDateDescriptor.parameters.size)
        localDateDescriptor.parameters.forEachIndexed { index, param ->
            assertScalarFieldShape(
                field = param,
                expectedIndex = index,
                expectedType = Int::class.java.name
            )
        }

        assertEquals(1, uriDescriptor.parameters.size)
        assertScalarFieldShape(
            field = uriDescriptor.parameters.single(),
            expectedIndex = 0,
            expectedType = String::class.java.name
        )

        assertEquals(1, localeDescriptor.parameters.size)
        assertScalarFieldShape(
            field = localeDescriptor.parameters.single(),
            expectedIndex = 0,
            expectedType = String::class.java.name
        )

        assertEquals(2, singletonMapDescriptor.parameters.size)
        assertScalarFieldShape(
            field = singletonMapDescriptor.parameters[0],
            expectedIndex = 0,
            expectedType = Any::class.java.name
        )
        assertScalarFieldShape(
            field = singletonMapDescriptor.parameters[1],
            expectedIndex = 1,
            expectedType = Any::class.java.name
        )

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
                    Value.Scalar(StringValue("https://example.com/a/b?x=1"))
                )
            )
        )

        val localeResponse: Value = invoke(
            InvocationRequest(
                executionId = localeDescriptor.executionId,
                args = listOf(
                    Value.Scalar(StringValue("en-AU"))
                )
            )
        )

        val singletonMapResponse: Value = invoke(
            InvocationRequest(
                executionId = singletonMapDescriptor.executionId,
                args = listOf(
                    Value.Scalar(StringValue("key1")),
                    Value.Scalar(StringValue("value1"))
                )
            )
        )

        assertScalarString(localDateResponse, "2026-04-28")
        assertScalarString(uriResponse, "https://example.com/a/b?x=1")

        val localeScalar: Value.Scalar = assertIs(localeResponse)
        assertEquals(StringValue("en-AU"), localeScalar.value)

        val mapResult: Value.MapValue = assertIs(singletonMapResponse)
        assertEquals(1, mapResult.entries.size)
    }

    private fun assertScalarFieldShape(
        field: FieldDescriptorDto,
        expectedIndex: Int,
        expectedType: String
    ) {
        assertEquals(expectedIndex, field.index)
        assertEquals(expectedType, field.type)
        assertEquals(FieldKindDto.SCALAR, field.kind)
        assertTrue(field.children.isEmpty())
        assertNotNull(field.reflectedName)
        assertNotNull(field.name)
    }

    @Test
    fun `invokes enum factory method from JSON`() {
        val response: Value = invokeSingleStatic(
            methodSource = StaticMethod(Month::class, "valueOf", String::class),
            args = listOf(
                Value.Scalar(StringValue("APRIL"))
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
        val localEngine: ReflectionEngine = ReflectionEngine(localConfig)
        val localRequestValueMapper: RequestValueMapper =
            RequestValueMapper(localScalarTypeRegistry)

        val execution: ExecutionContext.Static =
            localEngine.executionContexts().single() as ExecutionContext.Static

        val descriptor: MethodDescriptor = localEngine.descriptor(execution.methodId)

        val request = InvocationRequest(
            executionId = execution.executionId,
            args = listOf(
                Value.Scalar(StringValue("NOT_A_MONTH"))
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
                Value.Scalar(StringValue("root")),
                Value.ListValue(
                    listOf(
                        Value.Scalar(StringValue("child")),
                        Value.Scalar(StringValue("leaf.txt"))
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
                Value.Scalar(StringValue("value-123"))
            )
        )

        val mapValue: Value.MapValue = assertIs(response)
        assertEquals(1, mapValue.entries.size)

        val entry = mapValue.entries.single()
        val key: Value.Scalar = assertIs(entry.key)
        val value: Value.Scalar = assertIs(entry.value)

        assertEquals(ScalarValue.NumberValue("123"), key.value)
        assertEquals(StringValue("value-123"), value.value)
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
        val localEngine: ReflectionEngine = ReflectionEngine(localConfig)
        val localRequestValueMapper: RequestValueMapper =
            RequestValueMapper(localScalarTypeRegistry)
        val localResponseValueMapper: ResponseValueMapper =
            ResponseValueMapper(localScalarTypeRegistry)

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
        assertEquals(StringValue(expected), scalar.value)
    }

    private fun assertScalarFieldShape(
        field: FieldDescriptorDto,
        expectedIndex: Int,
        expectedType: String,
        expectedNullable: Boolean
    ) {
        assertEquals(expectedIndex, field.index)
        assertEquals(expectedType, field.type)
        assertEquals(expectedNullable, field.nullable)
        assertEquals(FieldKindDto.SCALAR, field.kind)
        assertTrue(field.children.isEmpty())
        assertNotNull(field.reflectedName)
        assertNotNull(field.name)
        assertTrue(field.reflectedName.isNotBlank())
        assertTrue(field.name.isNotBlank())
    }

    private fun isDescriptorScalarLike(type: Class<*>): Boolean =
        requestValueMapper.isScalarLike(type) ||
                type == Any::class.java ||
                type == Object::class.java

    private fun toExecutionDescriptorDto(
        executionContext: ExecutionContext,
        descriptor: MethodDescriptor
    ): ExecutionDescriptorDto =
        ExecutionDescriptorDto(
            executionId = executionContext.executionId,
            sourceDescription = executionContext.sourceDescription,
            reflectedName = descriptor.reflectedName,
            displayName = descriptor.displayName,
            returnType = descriptor.returnType.name,
            isStatic = descriptor.isStatic,
            parameters = descriptor.parameters.map { param: ParamDescriptor ->
                toFieldDescriptorDto(param)
            }
        )

    private fun toFieldDescriptorDto(
        param: ParamDescriptor
    ): FieldDescriptorDto =
        FieldDescriptorDto(
            index = param.index,
            reflectedName = param.reflectedName,
            name = param.name,
            type = param.logicalType.name,
            nullable = param.nullable,
            kind = if (isDescriptorScalarLike(param.logicalType)) {
                FieldKindDto.SCALAR
            } else {
                FieldKindDto.RECORD
            },
            children = emptyList()
        )
}