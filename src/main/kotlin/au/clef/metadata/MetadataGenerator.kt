package au.clef.metadata

import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.ParamDescriptor
import au.clef.engine.registry.ReflectionRegistry
import au.clef.metadata.model.MetadataRoot
import au.clef.metadata.model.MethodMetadata
import au.clef.metadata.model.ParamMetadata

class MetadataGenerator(private val reflectionRegistry: ReflectionRegistry) {

    private fun generate(clazz: Class<*>): MetadataRoot {
        val descriptors: List<MethodDescriptor> = reflectionRegistry.descriptors(clazz)
        val methods: Map<MethodId, MethodMetadata> =
            descriptors
                .sortedBy { descriptor: MethodDescriptor -> descriptor.reflectedName }
                .associate { descriptor: MethodDescriptor ->
                    descriptor.id to MethodMetadata(
                        parameters = descriptor.parameters.map { param: ParamDescriptor ->
                            ParamMetadata(name = defaultParameterName(param))
                        }
                    )
                }
        return MetadataRoot(methods = methods)
    }

    fun generate(): MetadataRoot {
        val methods: Map<MethodId, MethodMetadata> =
            reflectionRegistry.targetClasses
                .flatMap { clazz: Class<*> ->
                    generate(clazz).methods.entries
                }
                .associate { entry: Map.Entry<MethodId, MethodMetadata> ->
                    entry.key to entry.value
                }

        return MetadataRoot(methods = methods)
    }

    private fun defaultParameterName(param: ParamDescriptor): String =
        if (param.name.startsWith("arg")) {
            "param${param.index}"
        } else {
            param.name
        }
}