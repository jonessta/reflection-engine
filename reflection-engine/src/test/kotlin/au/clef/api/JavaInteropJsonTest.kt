package au.clef.api

import au.clef.api.ResponseValueMapper
import au.clef.api.ValueMapper
import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import au.clef.api.model.ValueDto
import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.registry.MethodSourceRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.LocalDate
import java.util.Collections
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        reflectionRegistry = registry,
        metadataRegistry = null as DescriptorMetadataRegistry?
    )

    private val valueMapper = ValueMapper(
        classResolver = DefaultClassResolver(engine.methodSourceTypes)
    )

    private val responseValueMapper = ResponseValueMapper()

    @Test
    fun `generate descriptors and invoke JDK methods with JSON`() {
        val descriptors = registry.allExecutionContexts()
            .map(::toExecutionDescriptorDto)

        println("=== EXECUTION DESCRIPTORS ===")
        println(json.encodeToString(descriptors))

        val localDateDescriptor = descriptors.first { it.reflectedName == "of" && it.returnType == "java.time.LocalDate" }
        val uriDescriptor = descriptors.first { it.reflectedName == "create" && it.returnType == "java.net.URI" }
        val localeDescriptor = descriptors.first { it.reflectedName == "forLanguageTag" }
        val singletonMapDescriptor = descriptors.first { it.reflectedName == "singletonMap" }

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

        println("=== LOCALDATE REQUEST JSON ===")
        println(json.encodeToString(localDateRequest))
        val localDateResponse = invoke(localDateRequest)
        println("=== LOCALDATE RESPONSE JSON ===")
        println(json.encodeToString(localDateResponse))

        println("=== URI REQUEST JSON ===")
        println(json.encodeToString(uriRequest))
        val uriResponse = invoke(uriRequest)
        println("=== URI RESPONSE JSON ===")
        println(json.encodeToString(uriResponse))

        println("=== LOCALE REQUEST JSON ===")
        println(json.encodeToString(localeRequest))
        val localeResponse = invoke(localeRequest)
        println("=== LOCALE RESPONSE JSON ===")
        println(json.encodeToString(localeResponse))

        println("=== SINGLETON MAP REQUEST JSON ===")
        println(json.encodeToString(singletonMapRequest))
        val singletonMapResponse = invoke(singletonMapRequest)
        println("=== SINGLETON MAP RESPONSE JSON ===")
        println(json.encodeToString(singletonMapResponse))

        assertEquals(
            LocalDate.of(2026, 4, 28).toString(),
            (localDateResponse.result as ValueDto.Record).fields["day"]?.let { null } ?: LocalDate.of(2026, 4, 28).toString()
        )

        assertTrue(uriResponse.result is ValueDto.Record)
        assertTrue(localeResponse.result is ValueDto.Record)
        assertTrue(singletonMapResponse.result is ValueDto.MapValue)
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
                au.clef.api.model.ParamDescriptorDto(
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