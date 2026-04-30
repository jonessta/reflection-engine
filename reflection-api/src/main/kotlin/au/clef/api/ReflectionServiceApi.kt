package au.clef.api

import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.InvocationResponse
import au.clef.api.model.ParamDescriptorDto
import au.clef.engine.ExecutionContext
import au.clef.engine.ReflectionConfig
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader

class ReflectionServiceApi(
    apiConfig: ReflectionApiConfig
) {
    private val reflectionConfig: ReflectionConfig = apiConfig.reflectionConfig

    private val metadataRegistry: DescriptorMetadataRegistry? =
        reflectionConfig.metadataResourcePath
            ?.let(MetadataLoader::fromResourceOrEmpty)
            ?.let(::DescriptorMetadataRegistry)

    private val engine = ReflectionEngine(
        reflectionConfig = reflectionConfig,
        metadataRegistry = metadataRegistry
    )

    private val classResolver = DefaultClassResolver(engine)

    private val requestValueMapper = RequestValueMapper(
        classResolver = classResolver,
        userDefinedScalarConverters = apiConfig.userDefinedScalarConverters
    )

    private val responseValueMapper = ResponseValueMapper(
        userDefinedScalarConverters = apiConfig.userDefinedScalarConverters
    )

    fun invoke(request: InvocationRequest): InvocationResponse {
        val executionContext = engine.executionContext(request.executionId)
        val descriptor = engine.descriptor(executionContext.methodId)

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
        engine.executionContexts().map { executionContext ->
            val descriptor = engine.descriptor(executionContext.methodId)
            toExecutionDescriptorDto(executionContext, descriptor)
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
                    type = param.type.name,
                    reflectedName = param.reflectedName,
                    name = param.name,
                    label = param.label,
                    nullable = param.nullable
                )
            }
        )
}