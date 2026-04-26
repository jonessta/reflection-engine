package au.clef.engine

import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

sealed class ExposedTarget {

    abstract val targetClass: KClass<*>

    interface InstanceLike {
        val id: String
        val obj: Any
    }

    /**
     * Expose all supported static methods on this class.
     */
    data class StaticClass(override val targetClass: KClass<*>) : ExposedTarget()

    /**
     * Expose exactly one static method.
     */
    data class StaticMethod(override val targetClass: KClass<*>, val methodId: MethodId) : ExposedTarget() {

        companion object {

            fun from(declaringClass: KClass<*>, methodName: String, vararg parameterTypes: KClass<*>): StaticMethod =
                StaticMethod(
                    targetClass = declaringClass,
                    methodId = MethodId.from(declaringClass, methodName, *parameterTypes)
                )

            fun from(function: KFunction<*>): StaticMethod {
                val method: Method = requireNotNull(function.javaMethod) {
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
     * If no id is supplied, a UUID is assigned. A user supplied id can be meaningful for the UI layer and
     * more human-readable ie accountingService rather than a UUID string.
     */
    data class Instance(
        override val obj: Any,
        override val id: String = UUID.randomUUID().toString()
    ) : ExposedTarget(), InstanceLike {
        override val targetClass: KClass<*> get() = obj::class
    }

    /**
     * Expose exactly one instance method on this object.
     *
     * If no id is supplied, a UUID is assigned.
     */
    data class InstanceMethod(
        override val obj: Any,
        val methodId: MethodId,
        /**
         * @param id The instance identifier of the service, user supplied or generated if not. Eg
         * val acmeService = AcmeService()
         * val target = ExposedTarget.from(acmeService, "numberOdWidgets", WidgetItem::class)
         * OR
         * val target = ExposedTarget.from(acmeService, "numberOdWidgets", WidgetItem::class, id="myWidgetService")
         */
        override val id: String = UUID.randomUUID().toString()
    ) : ExposedTarget(), InstanceLike {
        override val targetClass: KClass<*> get() = obj::class

        companion object {

            fun from(
                obj: Any,
                methodName: String,
                vararg parameterTypes: KClass<*>,
                id: String = UUID.randomUUID().toString()
            ): InstanceMethod = InstanceMethod(
                obj = obj,
                methodId = MethodId.from(obj::class, methodName, *parameterTypes),
                id = id
            )
        }
    }
}