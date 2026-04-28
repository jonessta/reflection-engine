package au.clef.engine

import au.clef.engine.model.MethodId

open class EngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class MethodNotFoundException(
    val methodId: MethodId, val available: List<String>
) : EngineException(
    "Method '${methodId}' not found. Available: ${available.joinToString()}"
)

class MissingInstanceException(methodName: String) : EngineException("Instance required for method $methodName")

class ObjectConstructionException(
    message: String,
    cause: Throwable? = null
) : EngineException(message, cause)