package au.clef.api

import au.clef.engine.registry.MethodSourceTypes

class DefaultClassResolver(
    methodSourceTypes: MethodSourceTypes,
    private val scalarTypeRegistry: ScalarTypeRegistry
) : ClassResolver {

    private val classesByName: Map<String, Class<*>> =
        methodSourceTypes.knownClasses
            .flatMap { clazz ->
                listOf(
                    clazz.name to clazz,
                    clazz.simpleName to clazz
                )
            }
            .toMap()

    override fun resolve(typeName: String): ResolvedType {
        val clazz = classesByName[typeName]
            ?: throw IllegalArgumentException("Unknown type: $typeName")

        return if (scalarTypeRegistry.isScalarLike(clazz)) {
            ResolvedType.Scalar(clazz)
        } else {
            ResolvedType.Structured(clazz)
        }
    }
}