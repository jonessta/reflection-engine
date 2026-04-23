package au.clef.engine

import au.clef.engine.model.MethodId
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

sealed class ExposedTarget {

    /**
     * Expose all supported static methods on this class.
     */
    data class StaticClass(val clazz: KClass<*>) : ExposedTarget()

    /**
     * Expose exactly one static method.
     */
    data class StaticMethod(val methodId: MethodId) : ExposedTarget()

    /**
     * Expose instance methods on this object via its id.
     */
    data class Instance(val id: String, val obj: Any) : ExposedTarget()
}