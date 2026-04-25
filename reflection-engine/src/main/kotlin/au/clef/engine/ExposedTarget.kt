package au.clef.engine

import au.clef.engine.model.MethodId
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

sealed class ExposedTarget {
    abstract val targetClass: KClass<*>

    data class StaticClass(
        override val targetClass: KClass<*>
    ) : ExposedTarget()

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

    data class Instance(
        val id: String,
        val obj: Any
    ) : ExposedTarget() {
        override val targetClass: KClass<*>
            get() = obj::class
    }
}
