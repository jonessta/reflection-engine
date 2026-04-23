package au.clef.engine

import kotlin.reflect.KClass

data class ReflectionAppDefinition(
    val targets: List<ExposedTarget>,
    val supportingTypes: List<KClass<*>> = emptyList(),
    val metadataResourcePath: String? = null
) {
    val targetClasses: List<KClass<*>>
        get() = targets.map {
            when (it) {
                is ExposedTarget.StaticClass -> it.clazz
                is ExposedTarget.Instance -> it.obj::class
            }
        }.distinct()

    val classes: List<KClass<*>>
        get() = (targetClasses + supportingTypes).distinct()

    val instancesById: Map<String, Any>
        get() = targets.mapNotNull {
            when (it) {
                is ExposedTarget.StaticClass -> null
                is ExposedTarget.Instance -> it.id to it.obj
            }
        }.toMap()
}

sealed class ExposedTarget {
    data class StaticClass(val clazz: KClass<*>) : ExposedTarget()
    data class Instance(val id: String, val obj: Any) : ExposedTarget()
}

