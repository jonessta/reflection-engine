package au.clef.api

import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import au.clef.api.model.ParamDescriptorDto
import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.registry.MethodSourceRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import kotlin.reflect.KClass

class ReflectionServiceApi(
    methodSources: Collection<MethodSource>,
    methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    metadataResourcePath: String? = null,
    userDefinedScalarConverters: List<ScalarConverter<out Any>> = emptyList()
) {

    private val methodSourceRegistry = MethodSourceRegistry(
        methodSources = methodSources,
        methodSupportingTypes = methodSupportingTypes
    )

    private val metadataRegistry: DescriptorMetadataRegistry? =
        metadataResourcePath
            ?.let(MetadataLoader::fromResourceOrEmpty)
            ?.let(::DescriptorMetadataRegistry)

    private val engine = ReflectionEngine(
        reflectionRegistry = methodSourceRegistry,
        metadataRegistry = metadataRegistry
    )

    private val requestValueMapper = RequestValueMapper(
        classResolver = DefaultClassResolver(engine.methodSourceTypes),
        userDefinedScalarConverters = userDefinedScalarConverters
    )

    private val responseValueMapper = ResponseValueMapper(
        userDefinedScalarConverters = userDefinedScalarConverters
    )

    constructor(
        methodSource: MethodSource,
        methodSupportingTypes: Collection<KClass<*>> = emptyList(),
        metadataResourcePath: String? = null,
        userDefinedScalarConverters: List<ScalarConverter<out Any>> = emptyList()
    ) : this(
        methodSources = listOf(methodSource),
        methodSupportingTypes = methodSupportingTypes,
        metadataResourcePath = metadataResourcePath,
        userDefinedScalarConverters = userDefinedScalarConverters
    )

    fun invoke(request: InvocationRequest): InvocationResponse {
        val executionContext: ExecutionContext =
            methodSourceRegistry.executionContext(request.executionId)

        val descriptor: MethodDescriptor = executionContext.descriptor

        val args: List<Any?> =
            request.args.zip(descriptor.parameters).map { (argDto, param) ->
                requestValueMapper.materialize(argDto, param.type)
            }

        val result: Any? = when (executionContext) {
            is ExecutionContext.Static ->
                engine.invokeStatic(descriptor, args)

            is ExecutionContext.Instance ->
                engine.invokeInstance(descriptor, executionContext.instance, args)
        }

        return InvocationResponse(
            result = responseValueMapper.toDtoValue(result)
        )
    }

    fun executionDescriptors(): List<ExecutionDescriptorDto> =
        methodSourceRegistry.allExecutionContexts().map(::toExecutionDescriptorDto)

    private fun toExecutionDescriptorDto(
        executionContext: ExecutionContext
    ): ExecutionDescriptorDto {
        val descriptor: MethodDescriptor = executionContext.descriptor

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