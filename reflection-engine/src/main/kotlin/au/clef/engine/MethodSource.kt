package au.clef.engine

import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

sealed class MethodSource {

    abstract val declaringClass: KClass<*>

    interface ExposableInstance {
        val id: String
        val instance: Any
    }

    /**
     * Expose all supported static methods on this class.
     */
    data class StaticClass(override val declaringClass: KClass<*>) : MethodSource()

    /**
     * Expose exactly one static method.
     */
    data class StaticMethod(override val declaringClass: KClass<*>, val methodId: MethodId) : MethodSource() {

        companion object {

            fun from(declaringClass: KClass<*>, methodName: String, vararg parameterTypes: KClass<*>): StaticMethod =
                StaticMethod(
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
     *
     * If no id is supplied, a UUID is assigned. A user supplied id can be meaningful for the UI layer and
     * more human-readable ie accountingService rather than a UUID string.
     */
    data class Instance(
        override val instance: Any,
        override val id: String = UUID.randomUUID().toString()
    ) : MethodSource(), ExposableInstance {

        override val declaringClass: KClass<*> get() = instance::class
    }

    /**
     * Expose exactly one instance method on this object.
     *
     * @param id The instance identifier of the service, user supplied or generated if not. Eg
     * val acmeService = AcmeService()
     * val methodSource = ExposedTarget.from(acmeService, "numberOdWidgets", WidgetItem::class)
     * OR
     * val methodSource = ExposedTarget.from(acmeService, "numberOdWidgets", WidgetItem::class, id="myWidgetService")
     */
    data class InstanceMethod(
        override val instance: Any,
        val methodId: MethodId,
        override val id: String = UUID.randomUUID().toString()
    ) : MethodSource(), ExposableInstance {

        override val declaringClass: KClass<*> get() = instance::class

        companion object {

            fun from(
                instance: Any,
                methodName: String,
                vararg parameterTypes: KClass<*>,
                id: String = UUID.randomUUID().toString()
            ): InstanceMethod {
                val klass: KClass<out Any> = instance::class
                require(klass.java.methods.any { it.name == methodName }) {
                    "Method $methodName not found on ${klass.simpleName}"
                }
                return InstanceMethod(
                    instance = instance,
                    methodId = MethodId.from(instance::class, methodName, *parameterTypes),
                    id = id
                )
            }
        }
    }
}