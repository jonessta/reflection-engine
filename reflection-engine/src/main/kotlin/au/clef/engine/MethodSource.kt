package au.clef.engine

import au.clef.engine.model.MethodId
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
    class StaticMethod : MethodSource {
        val methodId: MethodId

        constructor(declaringClass: KClass<*>, methodName: String, vararg parameterTypes: KClass<*>)
                : super(declaringClass) {
            this.methodId = MethodId.from(declaringClass, methodName, *parameterTypes)
        }

        constructor(function: KFunction<*>)
                : super(declaringClass = requireNotNull(function.javaMethod) { "Function ${function.name} does not have a Java method" }.declaringClass.kotlin) {
            this.methodId = MethodId.from(function.javaMethod!!)
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

        constructor(
            instance: Any,
            instanceDescription: String,
            methodName: String,
            vararg parameterTypes: KClass<*>
        ) : this(
            instance = instance,
            instanceDescription = instanceDescription,
            methodId = validatedMethodId(
                declaringClass = instance::class,
                methodName = methodName,
                parameterTypes = parameterTypes
            )
        )

        constructor(
            instance: Any,
            instanceDescription: String,
            function: KFunction<*>
        ) : this(
            instance = instance,
            instanceDescription = instanceDescription,
            methodId = MethodId.from(
                requireNotNull(function.javaMethod) {
                    "Function ${function.name} does not have a Java method"
                }
            )
        )

        private companion object {
            fun validatedMethodId(
                declaringClass: KClass<*>,
                methodName: String,
                parameterTypes: Array<out KClass<*>>
            ): MethodId {
                val requestedId: MethodId =
                    MethodId.from(declaringClass, methodName, *parameterTypes)

                val matchingFunction: KFunction<*>? =
                    declaringClass.members
                        .filterIsInstance<KFunction<*>>()
                        .firstOrNull { function: KFunction<*> ->
                            function.name == methodName &&
                                    function.parameters
                                        .filter { it.kind == kotlin.reflect.KParameter.Kind.VALUE }
                                        .mapNotNull { it.type.classifier as? KClass<*> } == parameterTypes.toList()
                        }

                if (matchingFunction != null) {
                    val javaMethod = requireNotNull(matchingFunction.javaMethod) {
                        "Function $methodName does not have a Java method"
                    }

                    val actualId: MethodId = MethodId.from(javaMethod)

                    require(requestedId == actualId) {
                        "Kotlin function '$methodName' must be registered using the KFunction-based constructor. " +
                                "The source-level signature resolves to JVM method '${actualId.value}', not '${requestedId.value}'."
                    }
                }

                return requestedId
            }
        }
    }
}