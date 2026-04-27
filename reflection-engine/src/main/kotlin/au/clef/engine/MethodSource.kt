package au.clef.engine

import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

sealed class MethodSource {

    abstract val declaringClass: KClass<*>

    interface ExposableInstance {
        val instanceDescription: String
        val instance: Any
    }

    /**
     * Expose all supported static methods on this class.
     */
    data class StaticClass(
        override val declaringClass: KClass<*>
    ) : MethodSource()

    /**
     * Expose exactly one static method.
     */
    data class StaticMethod(
        override val declaringClass: KClass<*>,
        val methodId: MethodId
    ) : MethodSource() {

        companion object {
            fun from(
                declaringClass: KClass<*>,
                methodName: String,
                vararg parameterTypes: KClass<*>
            ): StaticMethod = StaticMethod(
                declaringClass = declaringClass,
                methodId = MethodId.from(declaringClass, methodName, *parameterTypes)
            )

            fun from(function: KFunction<*>): StaticMethod {
                val method: Method = requireNotNull(function.javaMethod) {
                    "Function ${function.name} does not have a Java method"
                }
                return StaticMethod(
                    declaringClass = method.declaringClass.kotlin,
                    methodId = MethodId.from(method)
                )
            }
        }
    }

    /**
     * Expose all instance methods on this object.
     */
    data class Instance(
        override val instance: Any,
        override val instanceDescription: String,
    ) : MethodSource(), ExposableInstance {
        override val declaringClass: KClass<*> get() = instance::class
    }

    /**
     * Expose exactly one instance method on this object.
     */
    data class InstanceMethod(
        override val instance: Any,
        override val instanceDescription: String,
        val methodId: MethodId
    ) : MethodSource(), ExposableInstance {

        override val declaringClass: KClass<*>
            get() = instance::class

        companion object {
            fun from(
                instance: Any,
                instanceDescription: String,
                methodName: String,
                vararg parameterTypes: KClass<*>
            ): InstanceMethod =
                InstanceMethod(
                    instance = instance,
                    instanceDescription = instanceDescription,
                    methodId = MethodId.from(instance::class, methodName, *parameterTypes)
                )
        }
    }
}