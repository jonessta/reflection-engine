package au.clef.api

class InstanceRegistry(
    private val instances: Map<String, Any>
) {
    fun get(id: String): Any =
        instances[id] ?: throw IllegalArgumentException("Unknown instance id: $id")
}