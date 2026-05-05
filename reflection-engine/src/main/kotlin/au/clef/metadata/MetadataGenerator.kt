package au.clef.metadata

import au.clef.engine.ReflectionConfig
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.ParamDescriptor
import au.clef.engine.registry.MethodSourceRegistry
import au.clef.metadata.model.MetadataRoot
import au.clef.metadata.model.MethodMetadata
import au.clef.metadata.model.ParamMetadata
import java.io.File

data class MetadataGenerationConfig(
    val reflectionConfig: ReflectionConfig,
    val outputFile: File
)

fun generateMetadata(config: MetadataGenerationConfig) {
    val methodSourceRegistry = MethodSourceRegistry(config.reflectionConfig)
    val metadata: MetadataRoot = MetadataGenerator(methodSourceRegistry).generate()
    MetadataWriter.writeToFile(metadata, config.outputFile)
}

class MetadataGenerator(private val methodSourceRegistry: MethodSourceRegistry) {

    private fun generateMethods(clazz: Class<*>): Map<MethodId, MethodMetadata> {
        val descriptors: List<MethodDescriptor> = methodSourceRegistry.descriptors(clazz)
        return descriptors
            .sortedBy { descriptor: MethodDescriptor -> descriptor.id.toString() }
            .associate { descriptor: MethodDescriptor ->
                descriptor.id to MethodMetadata(
                    parameters = descriptor.parameters.map { param: ParamDescriptor ->
                        ParamMetadata(name = defaultParameterName(param))
                    }
                )
            }
    }

    fun generate(): MetadataRoot {
        val methods: Map<MethodId, MethodMetadata> = methodSourceRegistry.declaringClasses
            .flatMap { clazz: Class<*> -> generateMethods(clazz).entries }
            .associate { entry: Map.Entry<MethodId, MethodMetadata> ->
                entry.key to entry.value
            }

        return MetadataRoot(methods)
    }

    private fun defaultParameterName(param: ParamDescriptor): String =
        if (param.name.startsWith("arg")) {
            "param${param.index}"
        } else {
            param.name
        }
}