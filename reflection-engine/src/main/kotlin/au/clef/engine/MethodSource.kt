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
        val instanceId: String
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
            ): StaticMethod =
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
     * If no id is supplied, a UUID is assigned. A user-supplied id can be more
     * meaningful for the UI layer, for example accountingService rather than a UUID.
     */
    data class Instance(
        override val instance: Any,
        override val instanceId: String = UUID.randomUUID().toString()
    ) : MethodSource(), ExposableInstance {

        override val declaringClass: KClass<*> get() = instance::class
    }

    /**
     * Expose exactly one instance method on this object.
     *
     * @param instanceId The instance identifier of the service, user-supplied or generated if not.
     * Example:
     * val acmeService = AcmeService()
     * val methodSource = MethodSource.InstanceMethod.from(acmeService, "numberOfWidgets", WidgetItem::class)
     * val namedMethodSource = MethodSource.InstanceMethod.from(
     *     instance = acmeService,
     *     methodName = "numberOfWidgets",
     *     WidgetItem::class,
     *     instanceId = "myWidgetService"
     * )
     */
    data class InstanceMethod(
        override val instance: Any,
        val methodId: MethodId,
        override val instanceId: String = UUID.randomUUID().toString()
    ) : MethodSource(), ExposableInstance {

        override val declaringClass: KClass<*> get() = instance::class

        companion object {

            fun from(
                instance: Any,
                methodName: String,
                vararg parameterTypes: KClass<*>,
                instanceId: String = UUID.randomUUID().toString()
            ): InstanceMethod = InstanceMethod(
                instance = instance,
                methodId = MethodId.from(instance::class, methodName, *parameterTypes),
                instanceId = instanceId
            )
        }
    }
}