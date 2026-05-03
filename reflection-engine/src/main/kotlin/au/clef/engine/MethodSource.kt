package au.clef.engine

import au.clef.engine.model.MethodId
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod

sealed class MethodSource(val declaringClass: KClass<*>) {

    sealed class InstanceSource(
        declaringClass: KClass<*>,
        val instance: Any,
        val instanceDescription: String
    ) : MethodSource(declaringClass)

    /**
     * Expose all supported static methods on this class.
     */
    class StaticClass(declaringClass: KClass<*>) : MethodSource(declaringClass)

    /**
     * Expose exactly one static method.
     */
    class StaticMethod : MethodSource {

        val methodId: MethodId

        constructor(
            declaringClass: KClass<*>,
            methodName: String,
            vararg parameterTypes: KClass<*>
        ) : super(declaringClass) {
            this.methodId = MethodId.from(declaringClass, methodName, *parameterTypes)
        }

        constructor(function: KFunction<*>) : super(
            declaringClass = requireNotNull(function.javaMethod) {
                "Function ${function.name} does not have a Java method"
            }.declaringClass.kotlin
        ) {
            this.methodId = MethodId.from(
                requireNotNull(function.javaMethod) {
                    "Function ${function.name} does not have a Java method"
                }
            )
        }
    }

    /**
     * Expose all instance methods on this object.
     */
    class Instance(instance: Any, instanceDescription: String) :
        InstanceSource(instance::class, instance, instanceDescription)

    /**
     * Expose exactly one instance method on this object.
     */
    class InstanceMethod(instance: Any, instanceDescription: String, val methodId: MethodId) :
        InstanceSource(instance::class, instance, instanceDescription) {

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
                    declaringClass.declaredMemberFunctions.firstOrNull { function: KFunction<*> ->
                        if (function.name != methodName) {
                            return@firstOrNull false
                        }

                        val valueParameters = function.parameters
                            .filter { parameter: KParameter ->
                                parameter.kind == KParameter.Kind.VALUE
                            }

                        if (valueParameters.size != parameterTypes.size) {
                            return@firstOrNull false
                        }

                        valueParameters.mapIndexed { index: Int, parameter: KParameter ->
                            val classifier = parameter.type.classifier as? KClass<*>
                                ?: return@firstOrNull false
                            classifier == parameterTypes[index]
                        }.all { it }
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