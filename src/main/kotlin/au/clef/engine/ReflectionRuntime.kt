package au.clef.engine

import au.clef.api.InstanceRegistry
import au.clef.app.web.ReflectionServiceApi
import au.clef.engine.registry.MethodRegistry
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.MetadataLoader

data class ReflectionRuntime(
    val methodRegistry: MethodRegistry,
    val engine: ReflectionEngine,
    val instanceRegistry: InstanceRegistry,
    val api: ReflectionServiceApi
)

fun createReflectionRuntime(definition: ReflectionAppDefinition): ReflectionRuntime {
    require(definition.classes.isNotEmpty()) { "At least one class must be registered" }

    val methodRegistry = MethodRegistry(
        definition.classes.first(),
        *definition.classes.drop(1).toTypedArray()
    )

    val metadataRegistry =
        definition.metadataResourcePath
            ?.let { MetadataLoader.fromResourceOrEmpty(it) }
            ?.let { DescriptorMetadataRegistry(it) }

    val engine = ReflectionEngine(
        methodRegistry = methodRegistry,
        metadataRegistry = metadataRegistry
    )

    val instanceRegistry = InstanceRegistry(definition.instances)

    val api = ReflectionServiceApi(
        engine = engine,
        instanceRegistry = instanceRegistry
    )

    return ReflectionRuntime(
        methodRegistry = methodRegistry,
        engine = engine,
        instanceRegistry = instanceRegistry,
        api = api
    )
}