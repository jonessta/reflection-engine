package au.clef.engine

import au.clef.engine.model.InheritanceLevel
import kotlin.reflect.KClass

data class ReflectionConfig(
    val methodSources: List<MethodSource>,
    val additionalTypes: List<KClass<*>> = emptyList(),
    val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) {

    init {
        require(methodSources.isNotEmpty()) { "methodSources must not be empty" }
    }
}

class ReflectionConfigBuilder(methodSources: Array<out MethodSource>) {

    private val methodSources: MutableList<MethodSource> = methodSources.toMutableList()
    private val additionalTypes: MutableList<KClass<*>> = mutableListOf()
    private var inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly

    fun addMethodSources(vararg sources: MethodSource): ReflectionConfigBuilder = apply {
        methodSources += sources
    }

    fun additionalTypes(vararg types: KClass<*>): ReflectionConfigBuilder = apply {
        additionalTypes += types
    }

    fun inheritanceLevel(value: InheritanceLevel): ReflectionConfigBuilder = apply {
        inheritanceLevel = value
    }

    fun build(): ReflectionConfig = ReflectionConfig(
        methodSources = methodSources.distinct(),
        additionalTypes = additionalTypes.distinct(),
        inheritanceLevel = inheritanceLevel,
    )
}

fun reflectionConfig(vararg methodSources: MethodSource): ReflectionConfigBuilder =
    ReflectionConfigBuilder(methodSources)
