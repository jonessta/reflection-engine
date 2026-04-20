package au.clef.engine

import au.clef.engine.model.MethodId
import au.clef.engine.model.Value

open class EngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class MethodNotFoundException(
    val methodId: MethodId, val available: List<String>
) : EngineException(
    "Method '${methodId}' not found. Available: ${available.joinToString()}"
)

class MissingInstanceException(methodName: String) : EngineException("Instance required for method $methodName")

class TypeMismatchException(
    value: Value,
    targetType: Class<*>
) : EngineException("Cannot convert $value to ${targetType.name}")

class ObjectConstructionException(
    message: String,
    cause: Throwable? = null
) : EngineException(message, cause)