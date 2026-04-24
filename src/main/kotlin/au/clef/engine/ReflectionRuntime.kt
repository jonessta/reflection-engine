package au.clef.engine

import au.clef.api.InstanceRegistry
import au.clef.app.web.ReflectionServiceApi
import au.clef.engine.registry.ReflectionRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader
import kotlin.reflect.KClass
// todo remove this file and refactor Main.kt
data class ReflectionAppDefinition(
    val targets: List<ExposedTarget>,
    val targetSupportingTypes: List<KClass<*>> = emptyList(),
    val metadataResourcePath: String? = null
) {

    constructor(
        target: ExposedTarget,
        targetSupportingTypes: List<KClass<*>> = emptyList(),
        metadataResourcePath: String? = null
    ) : this(
        targets = listOf(target),
        targetSupportingTypes = targetSupportingTypes,
        metadataResourcePath = metadataResourcePath
    )

    val targetClasses: List<KClass<*>>
        get() = targets.map { exposedTarget: ExposedTarget -> exposedTarget.targetClass }.distinct()

    val classes: List<KClass<*>>
        get() = (targetClasses + targetSupportingTypes).distinct()

    val instancesById: Map<String, Any>
        get() = targets.mapNotNull { target ->
            when (target) {
                is ExposedTarget.Instance -> target.id to target.obj
                else -> null
            }
        }.toMap()
}

data class ReflectionRuntime(
    val reflectionRegistry: ReflectionRegistry,
    val engine: ReflectionEngine,
    val instanceRegistry: InstanceRegistry,
    val api: ReflectionServiceApi
)

fun createReflectionRuntime(definition: ReflectionAppDefinition): ReflectionRuntime {
    require(definition.targetClasses.isNotEmpty()) {
        "At least one target class must be registered"
    }
    val reflectionRegistry = ReflectionRegistry(definition.targetClasses, definition.classes)

    val metadataRegistry: DescriptorMetadataRegistry? = definition.metadataResourcePath
        ?.let(MetadataLoader::fromResourceOrEmpty)
        ?.let(::DescriptorMetadataRegistry)

    val engine = ReflectionEngine(
        reflectionRegistry = reflectionRegistry,
        metadataRegistry = metadataRegistry
    )

    val instanceRegistry = InstanceRegistry(definition.instancesById)

    val api = ReflectionServiceApi(definition.targets, definition.targetSupportingTypes, definition.metadataResourcePath)

    return ReflectionRuntime(
        reflectionRegistry = reflectionRegistry,
        engine = engine,
        instanceRegistry = instanceRegistry,
        api = api
    )
}