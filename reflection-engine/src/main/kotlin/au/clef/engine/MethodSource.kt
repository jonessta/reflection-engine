package au.clef.engine

import au.clef.engine.model.MethodId
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod

sealed class MethodSource(val declaringClass: KClass<*>, val sourceDescription: String? = null) {

    /**
     * Expose all supported static methods on this class.
     */
    class StaticClass(declaringClass: KClass<*>, sourceDescription: String? = null) :
        MethodSource(declaringClass = declaringClass, sourceDescription = sourceDescription)

    /**
     * Expose exactly one static method.
     */
    class StaticMethod(
        declaringClass: KClass<*>,
        val methodId: MethodId,
        sourceDescription: String? = null
    ) : MethodSource(declaringClass = declaringClass, sourceDescription = sourceDescription) {

        constructor(
            declaringClass: KClass<*>,
            methodName: String,
            vararg parameterTypes: KClass<*>
        ) : this(
            declaringClass = declaringClass,
            methodId = MethodId.from(declaringClass, methodName, *parameterTypes)
        )

        constructor(
            declaringClass: KClass<*>,
            sourceDescription: String?,
            methodName: String,
            vararg parameterTypes: KClass<*>
        ) : this(
            declaringClass = declaringClass,
            methodId = MethodId.from(declaringClass, methodName, *parameterTypes),
            sourceDescription = sourceDescription
        )

        constructor(
            function: KFunction<*>,
            sourceDescription: String? = null
        ) : this(
            declaringClass = requireNotNull(function.javaMethod) {
                "Function ${function.name} does not have a Java method"
            }.declaringClass.kotlin,
            methodId = MethodId.from(
                requireNotNull(function.javaMethod) {
                    "Function ${function.name} does not have a Java method"
                }
            ),
            sourceDescription = sourceDescription
        )
    }

    /**
     * Expose all instance methods on this object.
     */
    class Instance(val instance: Any, sourceDescription: String) : MethodSource(
        declaringClass = instance::class,
        sourceDescription = sourceDescription
    )

    /**
     * Expose exactly one instance method on this object.
     */
    class InstanceMethod(val instance: Any, sourceDescription: String, val methodId: MethodId) :
        MethodSource(
            declaringClass = instance::class,
            sourceDescription = sourceDescription
        ) {

        constructor(
            instance: Any,
            sourceDescription: String,
            methodName: String,
            vararg parameterTypes: KClass<*>
        ) : this(
            instance = instance,
            sourceDescription = sourceDescription,
            methodId = validatedMethodId(
                declaringClass = instance::class,
                methodName = methodName,
                parameterTypes = parameterTypes
            )
        )

        constructor(instance: Any, sourceDescription: String, function: KFunction<*>) : this(
            instance = instance,
            sourceDescription = sourceDescription,
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
                val requestedId =
                    MethodId.from(declaringClass, methodName, *parameterTypes)

                val matchingFunction: KFunction<*>? =
                    declaringClass.declaredMemberFunctions.firstOrNull { function: KFunction<*> ->
                        if (function.name != methodName) {
                            return@firstOrNull false
                        }

                        val valueParameters: List<KParameter> =
                            function.parameters.filter { parameter: KParameter ->
                                parameter.kind == KParameter.Kind.VALUE
                            }

                        if (valueParameters.size != parameterTypes.size) {
                            return@firstOrNull false
                        }

                        valueParameters
                            .mapIndexed { index: Int, parameter: KParameter ->
                                val classifier: KClass<*> =
                                    parameter.type.classifier as? KClass<*>
                                        ?: return@firstOrNull false
                                classifier == parameterTypes[index]
                            }
                            .all { matches: Boolean -> matches }
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