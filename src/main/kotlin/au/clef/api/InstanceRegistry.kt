package au.clef.api

import au.clef.EngineException

class InstanceRegistry(
    private val instances: Map<String, Any>
) {
    fun get(id: String): Any =
        instances[id] ?: throw EngineException("No instance registered for id '$id'")
}