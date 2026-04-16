package au.clef

class MetadataGenerator(
    private val methodRegistry: MethodRegistry = MethodRegistry()
) {

    fun generate(
        clazz: Class<*>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): MetadataRoot {
        val descriptors: List<MethodDescriptor> =
            methodRegistry.bindings(clazz, inheritanceLevel).map { it.descriptor }

        val methods: Map<String, MethodMetadata> =
            descriptors
                .sortedBy { it.name }
                .associate { descriptor: MethodDescriptor ->
                    buildMethodKey(descriptor) to MethodMetadata(
                        parameters = descriptor.parameters.map { param: ParamDescriptor ->
                            ParamMetadata(
                                name = defaultParameterName(param)
                            )
                        }
                    )
                }

        return MetadataRoot(
            classes = mapOf(
                clazz.name to ClassMetadata(methods = methods)
            )
        )
    }

    fun generate(
        classes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): MetadataRoot {
        val classEntries: Map<String, ClassMetadata> =
            classes.associate { clazz: Class<*> ->
                val single: MetadataRoot = generate(clazz, inheritanceLevel)
                clazz.name to (single.classes[clazz.name] ?: ClassMetadata())
            }

        return MetadataRoot(classes = classEntries)
    }

    private fun buildMethodKey(descriptor: MethodDescriptor): String =
        descriptor.id.substringAfter("#")

    private fun defaultParameterName(param: ParamDescriptor): String =
        if (param.name.startsWith("arg")) "param${param.index}" else param.name
}