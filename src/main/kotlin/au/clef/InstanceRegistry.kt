package au.clef

class InstanceRegistry(
    private val instances: Map<String, Any>
) {
    fun get(id: String): Any =
        instances[id] ?: throw EngineException("No instance registered for id '$id'")
}