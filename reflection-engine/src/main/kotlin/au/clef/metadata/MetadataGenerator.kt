package au.clef.metadata

import au.clef.engine.MethodSource
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.ParamDescriptor
import au.clef.engine.registry.MethodSourceRegistry
import au.clef.metadata.model.MetadataRoot
import au.clef.metadata.model.MethodMetadata
import au.clef.metadata.model.ParamMetadata
import java.io.File
import kotlin.reflect.KClass

data class MetadataGenerationConfig(
    val methodSources: Collection<MethodSource>,
    val methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    val outputFile: File
)

fun generateMetadata(config: MetadataGenerationConfig): Unit {
    val methodSourceRegistry: MethodSourceRegistry =
        MethodSourceRegistry(
            methodSources = config.methodSources,
            methodSupportingTypes = config.methodSupportingTypes
        )

    val metadata: MetadataRoot =
        MetadataGenerator(methodSourceRegistry).generate()

    MetadataWriter.writeToFile(metadata, config.outputFile)
}

class MetadataGenerator(
    private val methodSourceRegistry: MethodSourceRegistry
) {

    private fun generateMethods(clazz: Class<*>): Map<MethodId, MethodMetadata> {
        val descriptors: List<MethodDescriptor> =
            methodSourceRegistry.descriptors(clazz)

        return descriptors
            .sortedBy { descriptor: MethodDescriptor -> descriptor.id.toString() }
            .associate { descriptor: MethodDescriptor ->
                descriptor.id to MethodMetadata(
                    parameters = descriptor.parameters.map { param: ParamDescriptor ->
                        ParamMetadata(
                            name = defaultParameterName(param)
                        )
                    }
                )
            }
    }

    fun generate(): MetadataRoot {
        val methods: Map<MethodId, MethodMetadata> =
            methodSourceRegistry.declaringClasses
                .flatMap { clazz: Class<*> ->
                    generateMethods(clazz).entries
                }
                .associate { entry: Map.Entry<MethodId, MethodMetadata> ->
                    entry.key to entry.value
                }

        return MetadataRoot(
            methods = methods
        )
    }

    private fun defaultParameterName(param: ParamDescriptor): String =
        if (param.name.startsWith("arg")) {
            "param${param.index}"
        } else {
            param.name
        }
}