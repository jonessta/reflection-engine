package au.clef.engine

import au.clef.engine.model.InheritanceLevel
import kotlin.reflect.KClass

data class ReflectionConfig(
    val methodSources: Collection<MethodSource>,
    val methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    val metadataResourcePath: String? = null,
    val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
)

class ReflectionConfigBuilder internal constructor(firstMethodSource: MethodSource) {

    private val methodSources = mutableListOf(firstMethodSource)
    private val methodSupportingTypes = mutableListOf<KClass<*>>()
    private var metadataResourcePath: String? = null
    private var inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly

    fun methodSources(vararg sources: MethodSource): ReflectionConfigBuilder = apply { methodSources += sources }

    fun supportingTypes(vararg types: KClass<*>): ReflectionConfigBuilder = apply { methodSupportingTypes += types }

    fun metadataResourcePath(path: String?): ReflectionConfigBuilder = apply { metadataResourcePath = path }

    fun inheritanceLevel(level: InheritanceLevel): ReflectionConfigBuilder = apply { inheritanceLevel = level }

    fun build(): ReflectionConfig =
        ReflectionConfig(
            methodSources = methodSources.toList(),
            methodSupportingTypes = methodSupportingTypes.toList(),
            metadataResourcePath = metadataResourcePath,
            inheritanceLevel = inheritanceLevel
        )
}

fun reflectionConfig(methodSource: MethodSource, vararg methodSources: MethodSource): ReflectionConfigBuilder =
    ReflectionConfigBuilder(methodSource).apply { methodSources(*methodSources) }
