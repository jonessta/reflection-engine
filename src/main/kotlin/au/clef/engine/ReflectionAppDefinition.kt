package au.clef.engine

import kotlin.reflect.KClass

data class ReflectionAppDefinition(
    val classes: List<KClass<*>>,
    val metadataResourcePath: String? = null,
    val instances: Map<String, Any> = emptyMap()
)