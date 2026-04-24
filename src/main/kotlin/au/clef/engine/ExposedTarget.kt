package au.clef.engine

import au.clef.engine.model.MethodId
import kotlin.reflect.KClass

sealed class ExposedTarget {

    abstract val targetClass: KClass<*>

    /**
     * Expose all supported static methods on this class.
     */
    data class StaticClass(override val targetClass: KClass<*>) : ExposedTarget()

    /**
     * Expose exactly one static method.
     */
    data class StaticMethod(private val methodId: MethodId) : ExposedTarget() {
        override val targetClass: KClass<*>
            get() = methodId.declaringClass.kotlin
    }

    /**
     * Expose instance methods on this object via its id.
     */
    data class Instance(val id: String, val obj: Any) : ExposedTarget() {
        override val targetClass: KClass<*>
            get() = obj::class
    }
}
