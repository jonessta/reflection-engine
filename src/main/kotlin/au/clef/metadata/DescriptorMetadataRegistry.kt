package au.clef.metadata

import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.ParamDescriptor
import au.clef.metadata.model.*
class DescriptorMetadataRegistry(
    private val metadata: MetadataRoot
) {

    fun apply(descriptor: MethodDescriptor): MethodDescriptor {
        val methodMeta: MethodMetadata = metadata.methods[descriptor.id] ?: return descriptor

        val updatedParams: List<ParamDescriptor> = descriptor.parameters.map { param: ParamDescriptor ->
            val paramMeta: ParamMetadata? = methodMeta.parameters.getOrNull(param.index)

            if (paramMeta == null) {
                param
            } else {
                ParamDescriptor(
                    index = param.index,
                    type = param.type,
                    reflectedName = param.reflectedName,
                    name = paramMeta.name ?: param.name,
                    label = paramMeta.label ?: param.label,
                    nullable = param.nullable
                )
            }
        }

        return MethodDescriptor(
            id = descriptor.id,
            method = descriptor.method,
            displayName = methodMeta.displayName ?: descriptor.displayName,
            parameters = updatedParams
        )
    }

    fun applyAll(descriptors: List<MethodDescriptor>): List<MethodDescriptor> =
        descriptors.map { descriptor: MethodDescriptor -> apply(descriptor) }
}