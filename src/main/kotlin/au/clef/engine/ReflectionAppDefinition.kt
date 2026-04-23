package au.clef.engine

import kotlin.reflect.KClass

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
        get() = targets.map {
            when (it) {
                is ExposedTarget.StaticClass -> it.clazz
                is ExposedTarget.StaticMethod -> it.methodId.declaringClass.kotlin
                is ExposedTarget.Instance -> it.obj::class
            }
        }.distinct()

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
