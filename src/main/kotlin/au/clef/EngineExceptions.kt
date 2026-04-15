package au.clef

open class EngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class MethodNotFoundException(
    val owner: Class<*>,
    val methodName: String,
    val parameterTypes: List<Class<*>>? = null,
    val staticOnly: Boolean? = null,
    val availableOverloads: List<String> = emptyList()
) : EngineException(
    buildString {
        val kind = when (staticOnly) {
            true -> "static method"
            false -> "method"
            null -> "method"
        }
        append("No $kind '$methodName")
        if (parameterTypes != null) {
            append("(")
            append(parameterTypes.joinToString(", ") { it.simpleName })
            append(")")
        }
        append("' found on ${owner.name}")
        if (availableOverloads.isNotEmpty()) {
            append(". Available overloads: ")
            append(availableOverloads.joinToString(" | "))
        }
    })

class MissingInstanceException(methodName: String) : EngineException("Instance required for method $methodName")

class TypeMismatchException(val value: Any?, targetType: Class<*>) :
    EngineException("Cannot convert $value to ${targetType.simpleName}")

class ObjectConstructionException(targetClass: Class<*>, details: String, cause: Throwable? = null) :
    EngineException("Failed to construct ${targetClass.name}: $details", cause)
