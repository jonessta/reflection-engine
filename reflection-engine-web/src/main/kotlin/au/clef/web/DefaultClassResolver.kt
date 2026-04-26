package au.clef.web

import au.clef.api.ClassResolver
import au.clef.engine.registry.MethodSourceTypes

class DefaultClassResolver(methodSourceTypes: MethodSourceTypes) : ClassResolver {

    private val classesByName: Map<String, Class<*>> =
        methodSourceTypes.knownClasses.flatMap { clazz: Class<*> ->
            listOf(
                clazz.name to clazz,
                clazz.simpleName to clazz
            )
        }.toMap()

    override fun resolve(typeName: String): Class<*> =
        classesByName[typeName] ?: throw IllegalArgumentException("Unknown type: $typeName")
}