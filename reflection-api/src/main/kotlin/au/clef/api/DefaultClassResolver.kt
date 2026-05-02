package au.clef.api

import au.clef.engine.registry.MethodSourceTypes

class DefaultClassResolver(
    methodSourceTypes: MethodSourceTypes,
    private val scalarRegistry: ScalarTypeRegistry
) : ClassResolver {

    private val classesByName: Map<String, Class<*>> = buildMap {
        val simpleNames: MutableMap<String, Class<*>> = mutableMapOf()
        val ambiguousSimpleNames: MutableSet<String> = mutableSetOf()

        methodSourceTypes.knownClasses.forEach { clazz: Class<*> ->
            put(clazz.name, clazz)

            val existing: Class<*>? = simpleNames.putIfAbsent(clazz.simpleName, clazz)
            if (existing != null && existing != clazz) {
                ambiguousSimpleNames += clazz.simpleName
            }
        }

        simpleNames
            .filterKeys { simpleName: String -> simpleName !in ambiguousSimpleNames }
            .forEach { (simpleName: String, clazz: Class<*>) ->
                put(simpleName, clazz)
            }
    }

    override fun resolve(typeName: String): ResolvedType {
        val clazz: Class<*> = classesByName[typeName]
            ?: throw IllegalArgumentException(
                buildString {
                    append("Unknown type: ")
                    append(typeName)
                    append(". ")
                    append("If this is a structured request/response type, add it to reflectionConfig(...).supportingTypes(...). ")
                    append("Known types: ")
                    append(classesByName.keys.sorted().joinToString(", "))
                }
            )

        return if (scalarRegistry.isScalarLike(clazz)) {
            ResolvedType.Scalar(clazz)
        } else {
            ResolvedType.Structured(clazz)
        }
    }
}