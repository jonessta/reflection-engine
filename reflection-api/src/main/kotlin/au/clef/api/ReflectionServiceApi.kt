package au.clef.api

import au.clef.api.json.reflectionApiJsonSerializersModule
import au.clef.api.model.*
import au.clef.engine.ConfigKnownTypeSource
import au.clef.engine.ExecutionContext
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.ParamDescriptor
import au.clef.engine.registry.KnownTypeSource
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import kotlinx.serialization.modules.SerializersModule

class ReflectionServiceApi(apiConfig: ReflectionApiConfig) {

    private val scalarRegistry: ScalarTypeRegistry = apiConfig.scalarTypeRegistry

    private val metadataRegistry: DescriptorMetadataRegistry? =
        apiConfig.metadataResourcePath
            ?.let(MetadataLoader::fromResource)
            ?.let(::DescriptorMetadataRegistry)

    private val engine = ReflectionEngine(apiConfig.reflectionConfig, metadataRegistry)

    private val requestMapper = RequestValueMapper(scalarRegistry)

    private val responseMapper = ResponseValueMapper(scalarRegistry)

    private val knownTypeSource: KnownTypeSource = ConfigKnownTypeSource(apiConfig.reflectionConfig)

    val jsonSerializersModule: SerializersModule = reflectionApiJsonSerializersModule(
        DefaultClassResolver(knownTypeSource, scalarRegistry)
    )

    fun invoke(request: InvocationRequest): Value {
        val context: ExecutionContext = engine.executionContext(request.executionId)
        val descriptor: MethodDescriptor = engine.descriptor(context.methodId)

        require(request.args.size == descriptor.parameters.size) {
            "Expected ${descriptor.parameters.size} args for ${descriptor.id}, got ${request.args.size}"
        }
        val args: List<Any?> =
            descriptor.parameters.mapIndexed { index: Int, param: ParamDescriptor ->
                requestMapper.materialize(request.args[index], param.runtimeType)
            }
        val result: Any? =
            when (context) {
                is ExecutionContext.Static -> engine.invokeStatic(descriptor, args)
                is ExecutionContext.Instance -> engine.invokeInstance(
                    descriptor,
                    context.instance,
                    args
                )
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
            sourceDescription = ctx.sourceDescription,
            reflectedName = desc.reflectedName,
            displayName = desc.displayName,
            returnType = desc.returnType.name,
            isStatic = desc.isStatic,
            parameters = desc.parameters.map { p: ParamDescriptor ->
                toFieldDescriptorDto(p)
            }
        )

    private fun toFieldDescriptorDto(
        param: ParamDescriptor
    ): FieldDescriptorDto =
        toFieldDescriptorDto(
            index = param.index,
            reflectedName = param.reflectedName,
            name = param.name,
            type = param.logicalType,
            nullable = param.nullable,
            visited = emptySet()
        )

    private fun toFieldDescriptorDto(
        index: Int? = null,
        reflectedName: String,
        name: String,
        type: Class<*>,
        nullable: Boolean,
        visited: Set<Class<*>>
    ): FieldDescriptorDto {
        val scalarLike: Boolean = requestMapper.isScalarLike(type)

        if (scalarLike) {
            return FieldDescriptorDto(
                index = index,
                reflectedName = reflectedName,
                name = name,
                type = type.name,
                nullable = nullable,
                kind = FieldKindDto.SCALAR
            )
        }

        if (type in visited) {
            return FieldDescriptorDto(
                index = index,
                reflectedName = reflectedName,
                name = name,
                type = type.name,
                nullable = nullable,
                kind = FieldKindDto.RECORD,
                children = emptyList()
            )
        }

        val nextVisited: Set<Class<*>> = visited + type

        val childDescriptors: List<FieldDescriptorDto> =
            type.declaredFields
                .asSequence()
                .filter { field ->
                    !java.lang.reflect.Modifier.isStatic(field.modifiers) && !field.isSynthetic
                }
                .sortedBy { field -> field.name }
                .map { field ->
                    toFieldDescriptorDto(
                        reflectedName = field.name,
                        name = field.name,
                        type = field.type,
                        nullable = !field.type.isPrimitive,
                        visited = nextVisited
                    )
                }
                .toList()

        return FieldDescriptorDto(
            index = index,
            reflectedName = reflectedName,
            name = name,
            type = type.name,
            nullable = nullable,
            kind = FieldKindDto.RECORD,
            children = childDescriptors
        )
    }
}