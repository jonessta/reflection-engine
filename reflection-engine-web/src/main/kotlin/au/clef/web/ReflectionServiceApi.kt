package au.clef.web

import au.clef.api.ClassResolver
import au.clef.api.InstanceRegistry
import au.clef.api.ResponseValueMapper
import au.clef.api.ValueMapper
import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import au.clef.api.model.ParamDescriptorDto
import au.clef.engine.ExecutionContext
import au.clef.engine.ExecutionId
import au.clef.engine.MethodSource
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.Value
import au.clef.engine.registry.ReflectionRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import kotlin.reflect.KClass

class ReflectionServiceApi(
    methodSources: Collection<MethodSource>,
    methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    metadataResourcePath: String? = null
) {
    private val reflectionRegistry = ReflectionRegistry(
        methodSources = methodSources,
        methodSupportingTypes = methodSupportingTypes
    )

    private val metadataRegistry: DescriptorMetadataRegistry? = metadataResourcePath
        ?.let(MetadataLoader::fromResourceOrEmpty)
        ?.let(::DescriptorMetadataRegistry)

    private val engine = ReflectionEngine(reflectionRegistry = reflectionRegistry, metadataRegistry = metadataRegistry)

    private val instanceRegistry = InstanceRegistry(
        methodSources
            .filterIsInstance<MethodSource.ExposableInstance>()
            .associate { it.id to it.instance }
    )

    private val classResolver: ClassResolver = DefaultClassResolver(reflectionTypes = engine.reflectionTypes)

    private val responseValueMapper = ResponseValueMapper()

    private val valueMapper = ValueMapper(instanceRegistry, classResolver)

    constructor(
        methodSource: MethodSource,
        methodSupportingTypes: Collection<KClass<*>> = emptyList(),
        metadataResourcePath: String? = null
    ) : this(
        methodSources = listOf(methodSource),
        methodSupportingTypes = methodSupportingTypes,
        metadataResourcePath = metadataResourcePath
    )

    fun invoke(request: InvocationRequest): InvocationResponse {
        val executionContext: ExecutionContext = reflectionRegistry.executionContext(
            ExecutionId(request.executionId)
        )
        val args: List<Value> = request.args.map(valueMapper::toEngineValue)

        val result = when (executionContext) {
            is ExecutionContext.Static -> {
                val descriptor = engine.descriptor(executionContext.methodId)
                engine.invokeStatic(descriptor, args)
            }

            is ExecutionContext.Instance -> {
                val descriptor = engine.descriptor(executionContext.methodId)
                val instance = instanceRegistry.get(executionContext.instanceId)
                engine.invokeInstance(descriptor, instance, args)
            }
        }

        return InvocationResponse(result = responseValueMapper.toDtoValue(result))
    }

    fun executionDescriptors(): List<ExecutionDescriptorDto> =
        reflectionRegistry.allExecutionContexts().map(::toExecutionDescriptorDto)

    private fun toExecutionDescriptorDto(
        executionContext: ExecutionContext
    ): ExecutionDescriptorDto {
        val descriptor = engine.descriptor(executionContext.methodId)

        return ExecutionDescriptorDto(
            executionId = executionContext.executionId.value,
            methodId = descriptor.id.value,
            instanceId = when (executionContext) {
                is ExecutionContext.Static -> null
                is ExecutionContext.Instance -> executionContext.instanceId
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