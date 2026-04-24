package au.clef.web

import au.clef.api.ClassResolver
import au.clef.engine.registry.ReflectionTypes

class DefaultClassResolver(reflectionTypes: ReflectionTypes) : ClassResolver {

    private val classesByName: Map<String, Class<*>> =
        reflectionTypes.classes.flatMap { clazz ->
            listOf(
                clazz.name to clazz,
                clazz.simpleName to clazz
            )
        }.toMap()

    override fun resolve(typeName: String): Class<*> =
        classesByName[typeName] ?: throw IllegalArgumentException("Unknown type: $typeName")
}