package au.clef.web

import au.clef.api.ClassResolver
import au.clef.api.ResponseValueMapper
import au.clef.api.ValueMapper
import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import au.clef.api.model.ParamDescriptorDto
import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.ParamDescriptor
import au.clef.engine.model.Value
import au.clef.engine.registry.MethodSourceRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import kotlin.reflect.KClass

class ReflectionServiceApi(
    methodSources: Collection<MethodSource>,
    methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    metadataResourcePath: String? = null
) {

    private val methodSourceRegistry = MethodSourceRegistry(
        methodSources = methodSources,
        methodSupportingTypes = methodSupportingTypes
    )

    private val metadataRegistry: DescriptorMetadataRegistry? = metadataResourcePath
        ?.let(MetadataLoader::fromResourceOrEmpty)
        ?.let(::DescriptorMetadataRegistry)

    private val engine =
        ReflectionEngine(reflectionRegistry = methodSourceRegistry, metadataRegistry = metadataRegistry)

    private val classResolver: ClassResolver = DefaultClassResolver(methodSourceTypes = engine.methodSourceTypes)

    private val responseValueMapper = ResponseValueMapper()

    private val valueMapper = ValueMapper(classResolver)

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
        val executionContext: ExecutionContext = methodSourceRegistry.executionContext(request.executionId)
        val args: List<Value> = request.args.map(valueMapper::toEngineValue)
        val methodId: MethodId = executionContext.methodId
        val descriptor: MethodDescriptor = engine.descriptor(methodId)
        val result: Any? = when (executionContext) {
            is ExecutionContext.Static -> engine.invokeStatic(descriptor, args)
            is ExecutionContext.Instance -> engine.invokeInstance(descriptor, executionContext.instance, args)
        }

        return InvocationResponse(responseValueMapper.toDtoValue(result))
    }

    fun executionDescriptors(): List<ExecutionDescriptorDto> =
        methodSourceRegistry.allExecutionContexts().map(::toExecutionDescriptorDto)

    private fun toExecutionDescriptorDto(executionContext: ExecutionContext): ExecutionDescriptorDto {
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
            parameters = descriptor.parameters.map { paramDesc: ParamDescriptor ->
                ParamDescriptorDto(
                    index = paramDesc.index,
                    type = paramDesc.type.name,
                    reflectedName = paramDesc.reflectedName,
                    name = paramDesc.name,
                    label = paramDesc.label,
                    nullable = paramDesc.nullable
                )
            }
        )
    }
}