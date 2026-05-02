package au.clef.engine.model

import au.clef.engine.EngineException
import java.lang.reflect.Method
import kotlin.reflect.KClass

class IllegalMethodIdException(msg: String) : EngineException("Invalid MethodId: $msg")

private const val CLASS_NAME_SEPARATOR = "#"

private fun formatMethodId(
    declaringClassName: String,
    methodName: String,
    parameterTypeNames: List<String>
): String =
    buildString {
        append(declaringClassName)
        append(CLASS_NAME_SEPARATOR)
        append(methodName)
        append("(")
        append(parameterTypeNames.joinToString(","))
        append(")")
    }

class MethodId private constructor(val value: String) {

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean =
        other is MethodId && value == other.value

    override fun hashCode(): Int =
        value.hashCode()

    companion object {

        private val METHOD_ID_OUTER_REGEX = Regex(
            """^([A-Za-z_][A-Za-z0-9_$.]*)$CLASS_NAME_SEPARATOR([A-Za-z_][A-Za-z0-9_$-]*)\((.*)\)$"""
        )

        private val TYPE_NAME_REGEX = Regex("""^[A-Za-z_][A-Za-z0-9_$.]*$""")

        /**
         * Builds a MethodId from a Java Method.
         *
         * This is appropriate for Java methods and for cases where the JVM method
         * name is intentionally the public identity.
         */
        fun from(method: Method): MethodId =
            MethodId(
                formatMethodId(
                    declaringClassName = method.declaringClass.name,
                    methodName = method.name,
                    parameterTypeNames = method.parameterTypes.map { it.name }
                )
            )

        /**
         * Builds a logical/source-level MethodId from the declared Kotlin-facing signature.
         *
         * This intentionally does NOT resolve or inspect the backing JVM Method, so inline/value
         * class methods keep their source name rather than exposing JVM mangling.
         */
        fun from(
            declaringClass: KClass<*>,
            methodName: String,
            vararg parameterTypes: KClass<*>
        ): MethodId =
            MethodId(
                formatMethodId(
                    declaringClassName = declaringClass.java.name,
                    methodName = methodName,
                    parameterTypeNames = parameterTypes.map { it.java.name }
                )
            )

        fun fromValue(value: String): MethodId {
            val match = METHOD_ID_OUTER_REGEX.matchEntire(value)
                ?: throw IllegalMethodIdException("expected <class>#<method>(<paramTypes>)")

            val declaringClassName = match.groupValues[1]
            val methodName = match.groupValues[2]
            val paramsPart = match.groupValues[3]

            val parameterTypeNames =
                if (paramsPart.isBlank()) {
                    emptyList()
                } else {
                    paramsPart.split(",").also { paramTypes ->
                        if (paramTypes.any(String::isBlank)) {
                            throw IllegalMethodIdException(
                                "parameter types must be comma-separated with no empty entries"
                            )
                        }
                        if (!paramTypes.all(TYPE_NAME_REGEX::matches)) {
                            throw IllegalMethodIdException("parameter type names are malformed")
                        }
                    }
                }

            return MethodId(
                formatMethodId(
                    declaringClassName = declaringClassName,
                    methodName = methodName,
                    parameterTypeNames = parameterTypeNames
                )
            )
        }
    }
}

