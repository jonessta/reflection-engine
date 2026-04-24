package au.clef.api

class InstanceRegistry(
    private val instances: Map<String, Any>
) {
    // todo throw proper exceptions maybe a engine wide general exception?
    fun get(id: String): Any =
        instances[id] ?: throw IllegalArgumentException("Unknown instance id: $id")
}