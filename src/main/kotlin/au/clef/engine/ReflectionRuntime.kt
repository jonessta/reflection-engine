package au.clef.engine

import au.clef.api.InstanceRegistry
import au.clef.app.web.ReflectionServiceApi
import au.clef.engine.registry.ReflectionRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader

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

    val api = ReflectionServiceApi(
        engine = engine,
        instanceRegistry = instanceRegistry
    )

    return ReflectionRuntime(
        reflectionRegistry = reflectionRegistry,
        engine = engine,
        instanceRegistry = instanceRegistry,
        api = api
    )
}