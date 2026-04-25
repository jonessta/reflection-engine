package au.clef.engine

import au.clef.engine.model.MethodId
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

@JvmInline
value class ExecutionId(val value: String) {
    override fun toString(): String = value
}

sealed class ExposedTarget {
    abstract val targetClass: KClass<*>

    /**
     * Expose all supported static methods on this class.
     */
    data class StaticClass(
        override val targetClass: KClass<*>
    ) : ExposedTarget()

    /**
     * Expose exactly one static method.
     */
    data class StaticMethod(
        override val targetClass: KClass<*>,
        val methodId: MethodId
    ) : ExposedTarget() {
        companion object {
            fun from(
                declaringClass: KClass<*>,
                methodName: String,
                vararg parameterTypes: KClass<*>
            ): StaticMethod =
                StaticMethod(
                    targetClass = declaringClass,
                    methodId = MethodId.from(declaringClass, methodName, *parameterTypes)
                )

            fun from(function: KFunction<*>): StaticMethod {
                val method = requireNotNull(function.javaMethod) {
                    "Function ${function.name} does not have a Java method"
                }
                return StaticMethod(
                    targetClass = method.declaringClass.kotlin,
                    methodId = MethodId.from(method)
                )
            }
        }
    }

    /**
     * Expose all instance methods on this object.
     *
     * If no id is supplied, a UUID is assigned.
     */
    data class Instance(
        val obj: Any,
        val id: String = UUID.randomUUID().toString()
    ) : ExposedTarget() {
        override val targetClass: KClass<*>
            get() = obj::class
    }

    /**
     * Expose exactly one instance method on this object.
     *
     * If no id is supplied, a UUID is assigned.
     */
    data class InstanceMethod(
        val obj: Any,
        val methodId: MethodId,
        val id: String = UUID.randomUUID().toString()
    ) : ExposedTarget() {
        override val targetClass: KClass<*>
            get() = obj::class

        companion object {
            fun from(
                obj: Any,
                methodName: String,
                vararg parameterTypes: KClass<*>,
                id: String = UUID.randomUUID().toString()
            ): InstanceMethod =
                InstanceMethod(
                    obj = obj,
                    methodId = MethodId.from(obj::class, methodName, *parameterTypes),
                    id = id
                )
        }
    }
}