package au.clef

class DescriptorMetadataRegistry(private val metadata: MetadataRoot) {

    fun apply(descriptor: MethodDescriptor): MethodDescriptor {
        val className: String = descriptor.rawMethod.declaringClass.name
        val methodKey: String = buildMethodKey(descriptor)
        val classMeta = metadata.classes[className] ?: return descriptor
        val methodMeta = classMeta.methods[methodKey] ?: return descriptor
        val updatedParams = descriptor.parameters.map { param ->
            val paramMeta = methodMeta.parameters.getOrNull(param.index)
            if (paramMeta == null) {
                param
            } else {
                param.copy(name = paramMeta.name ?: param.name)
            }
        }
        return descriptor.copy(parameters = updatedParams)
    }

    fun applyAll(descriptors: List<MethodDescriptor>): List<MethodDescriptor> = descriptors.map { apply(it) }

    private fun buildMethodKey(descriptor: MethodDescriptor): String {
        val paramTypes = descriptor.rawMethod.parameterTypes.joinToString(",") { it.name }
        return "${descriptor.name}($paramTypes)"
    }
}