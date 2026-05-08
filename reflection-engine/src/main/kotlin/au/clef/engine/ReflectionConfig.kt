package au.clef.engine

import au.clef.engine.model.InheritanceLevel
import au.clef.engine.registry.KnownTypeSource
import kotlin.reflect.KClass

data class ReflectionConfig(
    val methodSources: Collection<MethodSource>,
    val methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) {

    init {
        require(methodSources.isNotEmpty()) { "methodSources must not be empty" }
    }
}

class ReflectionConfigBuilder internal constructor(firstMethodSource: MethodSource) {

    private val methodSources = mutableListOf(firstMethodSource)
    private val methodSupportingTypes = mutableListOf<KClass<*>>()
    private var inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly

    fun methodSources(vararg sources: MethodSource): ReflectionConfigBuilder =
        apply { methodSources += sources }

    fun supportingTypes(vararg types: KClass<*>): ReflectionConfigBuilder =
        apply { methodSupportingTypes += types }

    fun inheritanceLevel(level: InheritanceLevel): ReflectionConfigBuilder =
        apply { inheritanceLevel = level }

    fun build(): ReflectionConfig =
        ReflectionConfig(
            methodSources = methodSources.toList(),
            methodSupportingTypes = methodSupportingTypes.toList(),
            inheritanceLevel = inheritanceLevel
        )
}

fun reflectionConfig(
    methodSource: MethodSource,
    vararg methodSources: MethodSource
): ReflectionConfigBuilder =
    ReflectionConfigBuilder(methodSource).apply { methodSources(*methodSources) }

class ConfigKnownTypeSource(
    methodSources: Collection<MethodSource>,
    methodSupportingTypes: Collection<KClass<*>> = emptyList()
) : KnownTypeSource {

    constructor(reflectionConfig: ReflectionConfig) : this(
        methodSources = reflectionConfig.methodSources,
        methodSupportingTypes = reflectionConfig.methodSupportingTypes
    )

    override val declaringClasses: List<Class<*>> = methodSources
        .map { it.declaringClass.java }
        .distinct()

    override val knownClasses: List<Class<*>> =
        (methodSources.map { it.declaringClass } + methodSupportingTypes)
            .distinct()
            .map { it.java }
}