package au.clef

class DescriptorMetadataRegistry(private val metadata: MetadataRoot) {

    fun apply(descriptor: MethodDescriptor): MethodDescriptor {
        val (className, methodKey) = splitId(descriptor.id)
        val classMeta = metadata.classes[className] ?: return descriptor
        val methodMeta = classMeta.methods[methodKey] ?: return descriptor

        val updatedParams = descriptor.parameters.map { param ->
            val paramMeta = methodMeta.parameters.getOrNull(param.index)
            if (paramMeta == null) {
                param
            } else {
                param.copy(
                    name = paramMeta.name ?: param.name,
                    label = paramMeta.label ?: param.label
                )
            }
        }

        return descriptor.copy(
            displayName = methodMeta.displayName ?: descriptor.displayName,
            parameters = updatedParams
        )
    }

    fun applyAll(descriptors: List<MethodDescriptor>): List<MethodDescriptor> =
        descriptors.map { apply(it) }

    // ---------------- helpers ----------------

    private fun splitId(id: String): Pair<String, String> {
        val parts = id.split("#", limit = 2)
        val className = parts[0]
        val methodKey = parts.getOrNull(1) ?: ""
        return className to methodKey
    }
}