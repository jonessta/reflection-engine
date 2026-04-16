package au.clef

class DescriptorMetadataRegistry(private val metadata: MetadataRoot) {

    fun apply(descriptor: MethodDescriptor): MethodDescriptor {
        val className: String = descriptor.method.declaringClass.name
        val methodKey: String = buildMethodKey(descriptor)
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
        return descriptor.copy(parameters = updatedParams)
    }

    fun applyAll(descriptors: List<MethodDescriptor>): List<MethodDescriptor> = descriptors.map { apply(it) }

    private fun buildMethodKey(descriptor: MethodDescriptor): String {
        val paramTypes = descriptor.method.parameterTypes.joinToString(",") { it.name }
        return "${descriptor.name}($paramTypes)"
    }
}