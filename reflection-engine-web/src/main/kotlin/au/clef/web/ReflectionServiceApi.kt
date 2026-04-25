package au.clef.web

import au.clef.api.ClassResolver
import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.ExecutionDescriptorDto
import au.clef.api.model.InvocationRequest
import au.clef.api.model.ParamDescriptorDto
import au.clef.engine.ExecutionContext
import au.clef.engine.ExecutionId
import au.clef.engine.ExposedTarget
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.Value
import au.clef.engine.registry.ReflectionRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import kotlin.reflect.KClass

class ReflectionServiceApi(
    targets: Collection<ExposedTarget>,
    targetSupportingTypes: Collection<KClass<*>> = emptyList(),
    metadataResourcePath: String? = null
) {
    private val reflectionRegistry = ReflectionRegistry(
        targets = targets,
        supportingTypes = targetSupportingTypes
    )

    private val metadataRegistry: DescriptorMetadataRegistry? =
        metadataResourcePath
            ?.let(MetadataLoader::fromResourceOrEmpty)
            ?.let(::DescriptorMetadataRegistry)

    private val engine = ReflectionEngine(
        reflectionRegistry = reflectionRegistry,
        metadataRegistry = metadataRegistry
    )

    private val instanceRegistry = InstanceRegistry(
        targets.mapNotNull { target ->
            when (target) {
                is ExposedTarget.Instance -> target.id to target.obj
                is ExposedTarget.InstanceMethod -> target.id to target.obj
                else -> null
            }
        }.toMap()
    )

    private val classResolver: ClassResolver =
        DefaultClassResolver(reflectionTypes = engine.reflectionTypes)

    private val valueMapper = ValueMapper(instanceRegistry, classResolver)

    constructor(
        target: ExposedTarget,
        targetSupportingTypes: Collection<KClass<*>> = emptyList(),
        metadataResourcePath: String? = null
    ) : this(
        targets = listOf(target),
        targetSupportingTypes = targetSupportingTypes,
        metadataResourcePath = metadataResourcePath
    )

    fun executionDescriptors(): List<ExecutionDescriptorDto> =
        reflectionRegistry.allExecutionContexts()
            .map(::toExecutionDescriptorDto)

    fun invoke(request: InvocationRequest): Any? {
        val executionContext = reflectionRegistry.executionContext(
            ExecutionId(request.executionId)
        )
        val args: List<Value> = request.args.map(valueMapper::toEngineValue)

        return when (executionContext) {
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
    }

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