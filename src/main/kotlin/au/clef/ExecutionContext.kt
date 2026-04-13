package au.clef

data class ExecutionContext(
    val instance: Any? = null,
    val services: Map<Class<*>, Any> = emptyMap()
)