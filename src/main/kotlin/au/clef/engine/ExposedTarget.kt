package au.clef.engine

import au.clef.engine.model.MethodId
import kotlin.reflect.KClass

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