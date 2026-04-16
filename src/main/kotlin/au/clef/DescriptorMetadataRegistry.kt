package au.clef

class DescriptorMetadataRegistry(private val metadata: MetadataRoot) {

    fun apply(descriptor: MethodDescriptor): MethodDescriptor {
        val (className: String, methodKey: String) = splitId(descriptor.id)
        val classMeta: ClassMetadata = metadata.classes[className] ?: return descriptor
        val methodMeta: MethodMetadata = classMeta.methods[methodKey] ?: return descriptor

        val updatedParams: List<ParamDescriptor> = descriptor.parameters.map { param ->
            val paramMeta: ParamMetadata? = methodMeta.parameters.getOrNull(param.index)
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

    private fun splitId(id: String): Pair<String, String> {
        val parts = id.split("#", limit = 2)
        val className = parts[0]
        val methodKey = parts.getOrNull(1) ?: ""
        return className to methodKey
    }
}