package au.clef.engine

import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

sealed class MethodSource(val declaringClass: KClass<*>) {

    interface ExposableInstance {
        val instanceDescription: String
        val instance: Any
    }

    /**
     * Expose all supported static methods on this class.
     */
    class StaticClass(declaringClass: KClass<*>) : MethodSource(declaringClass)

    /**
     * Expose exactly one static method.
     */
    class StaticMethod(
        declaringClass: KClass<*>,
        val methodId: MethodId
    ) : MethodSource(declaringClass) {

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
    class Instance(
        override val instance: Any,
        override val instanceDescription: String
    ) : MethodSource(instance::class), ExposableInstance

    /**
     * Expose exactly one instance method on this object.
     */
    class InstanceMethod(
        override val instance: Any,
        override val instanceDescription: String,
        val methodId: MethodId
    ) : MethodSource(instance::class), ExposableInstance {

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