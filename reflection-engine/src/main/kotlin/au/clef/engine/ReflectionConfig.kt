package au.clef.engine

import au.clef.engine.model.InheritanceLevel
import kotlin.reflect.KClass

data class ReflectionConfig(
    val methodSources: Collection<MethodSource>,
    val methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    val metadataResourcePath: String? = null,
    val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) {
    constructor(
        methodSource: MethodSource,
        methodSupportingTypes: Collection<KClass<*>> = emptyList(),
        metadataResourcePath: String? = null,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    )
            : this(listOf(methodSource), methodSupportingTypes, metadataResourcePath, inheritanceLevel)
}