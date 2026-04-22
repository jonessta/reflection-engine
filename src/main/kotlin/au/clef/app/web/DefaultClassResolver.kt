package au.clef.app.web

import au.clef.api.ClassResolver
import au.clef.engine.registry.RegisteredClasses

class DefaultClassResolver(registeredClasses: RegisteredClasses) : ClassResolver {

    private val classesByName: Map<String, Class<*>> =
        registeredClasses.classes().flatMap { clazz ->
            listOf(
                clazz.name to clazz,
                clazz.simpleName to clazz
            )
        }.toMap()

    override fun resolve(typeName: String): Class<*> =
        classesByName[typeName]
            ?: throw IllegalArgumentException("Unknown type: $typeName")
}