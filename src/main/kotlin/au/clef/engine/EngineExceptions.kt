package au.clef.engine

import au.clef.engine.model.MethodId

open class EngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class MethodNotFoundException(
    val methodId: MethodId, val available: List<String>
) : RuntimeException(
    "Method '${methodId}' not found. Available: ${available.joinToString()}"
)

class MissingInstanceException(methodName: String) : EngineException("Instance required for method $methodName")

class TypeMismatchException(val value: Any?, targetType: Class<*>) :
    EngineException("Cannot convert $value to ${targetType.simpleName}")

class ObjectConstructionException(targetClass: Class<*>, details: String, cause: Throwable? = null) :
    EngineException("Failed to construct ${targetClass.name}: $details", cause)
