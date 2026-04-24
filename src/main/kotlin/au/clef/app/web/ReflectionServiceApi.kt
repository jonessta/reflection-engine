package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.InvocationRequest
import au.clef.engine.ExposedTarget
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.Value
import au.clef.engine.registry.ReflectionRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import kotlin.reflect.KClass

class ReflectionServiceApi(
    targets: List<ExposedTarget>,
    targetSupportingTypes: List<KClass<*>> = emptyList(),
    metadataResourcePath: String? = null
) {

    constructor(
        target: ExposedTarget,
        targetSupportingTypes: List<KClass<*>> = emptyList(),
        metadataResourcePath: String? = null
    ) : this(listOf(target), targetSupportingTypes, metadataResourcePath)

    private val reflectionRegistry = ReflectionRegistry(
        targets = targets,
        supportingClasses = targetSupportingTypes
    )

    private val metadataRegistry = metadataResourcePath
        ?.let(MetadataLoader::fromResourceOrEmpty)
        ?.let(::DescriptorMetadataRegistry)

    private val engine = ReflectionEngine(
        reflectionRegistry = reflectionRegistry,
        metadataRegistry = metadataRegistry
    )

    private val instanceRegistry =
        InstanceRegistry(targets.filterIsInstance<ExposedTarget.Instance>().associate { it.id to it.obj })

    private val classResolver = DefaultClassResolver(engine.reflectionTypes)
    private val valueMapper = ValueMapper(instanceRegistry, classResolver)

    fun invoke(request: InvocationRequest): Any? {
        val methodId: MethodId = MethodId.fromValue(request.methodId)
        val descriptor: MethodDescriptor = engine.descriptor(methodId)
        val args: List<Value> = request.args.map(valueMapper::toEngineValue)

        return if (descriptor.isStatic) {
            if (request.instanceId != null) {
                throw IllegalArgumentException("Method ${descriptor.id.value} is static and must not specify targetId")
            }
            engine.invokeStatic(descriptor, args)
        } else {
            val instanceId: String = request.instanceId
                ?: throw IllegalArgumentException("Method ${descriptor.id.value} is an instance method and requires targetId")
            val instance = instanceRegistry.get(instanceId)
            engine.invokeInstance(descriptor, instance, args)
        }
    }

    fun descriptors(className: String): List<MethodDescriptor> {
        val clazz: Class<*> = classResolver.resolve(className)
        return engine.descriptors(clazz)
    }
}