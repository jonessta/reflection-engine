package au.clef.api

import au.clef.api.json.valueSerializersModule
import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.ParamDescriptorDto
import au.clef.api.model.Value
import au.clef.engine.ExecutionContext
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.ParamDescriptor
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader

class ReflectionServiceApi(
    apiConfig: ReflectionApiConfig
) {

    private val scalarRegistry: ScalarTypeRegistry = apiConfig.scalarTypeRegistry

    private val metadataRegistry: DescriptorMetadataRegistry? =
        apiConfig.reflectionConfig.metadataResourcePath
            ?.let(MetadataLoader::fromResource)
            ?.let(::DescriptorMetadataRegistry)

    private val engine: ReflectionEngine =
        ReflectionEngine(apiConfig.reflectionConfig, metadataRegistry)

    private val requestMapper: RequestValueMapper =
        RequestValueMapper(scalarRegistry)

    private val responseMapper: ResponseValueMapper =
        ResponseValueMapper(scalarRegistry)

    fun serializersModule() = valueSerializersModule(
        DefaultClassResolver(engine, scalarRegistry)
    )

    fun invoke(request: InvocationRequest): Value {
        val context: ExecutionContext = engine.executionContext(request.executionId)
        val descriptor: MethodDescriptor = engine.descriptor(context.methodId)

        require(request.args.size == descriptor.parameters.size) {
            "Expected ${descriptor.parameters.size} args for ${descriptor.id}, got ${request.args.size}"
        }

        val args: List<Any?> =
            request.args.zip(descriptor.parameters) { value: Value, param: ParamDescriptor ->
                requestMapper.materialize(value, param.runtimeType)
            }

        val result: Any? =
            when (context) {
                is ExecutionContext.Static ->
                    engine.invokeStatic(descriptor, args)

                is ExecutionContext.Instance ->
                    engine.invokeInstance(descriptor, context.instance, args)
            }

        return responseMapper.toValue(result)
    }

    fun executionDescriptors(): List<ExecutionDescriptorDto> =
        engine.executionContexts()
            .map { ctx: ExecutionContext ->
                toExecutionDescriptorDto(ctx, engine.descriptor(ctx.methodId))
            }

    private fun toExecutionDescriptorDto(
        ctx: ExecutionContext,
        desc: MethodDescriptor
    ): ExecutionDescriptorDto =
        ExecutionDescriptorDto(
            executionId = ctx.executionId,
            instanceDescription = (ctx as? ExecutionContext.Instance)?.instanceDescription,
            reflectedName = desc.reflectedName,
            displayName = desc.displayName,
            returnType = desc.returnType.name,
            isStatic = desc.isStatic,
            parameters = desc.parameters.map { p: ParamDescriptor ->
                ParamDescriptorDto(
                    index = p.index,
                    type = p.logicalType.name,
                    reflectedName = p.reflectedName,
                    name = p.name,
                    nullable = p.nullable,
                    scalarLike = requestMapper.isScalarLike(p.logicalType)
                )
            }
        )
}