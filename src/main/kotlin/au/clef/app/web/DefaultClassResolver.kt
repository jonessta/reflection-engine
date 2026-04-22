package au.clef.app.web

import au.clef.api.ClassResolver

class DefaultClassResolver(
    classes: List<Class<*>>
) : ClassResolver {

    private val classesByName: Map<String, Class<*>> =
        classes.flatMap { clazz ->
            listOf(
                clazz.name to clazz,
                clazz.simpleName to clazz
            )
        }.toMap()

    override fun resolve(typeName: String): Class<*> =
        classesByName[typeName]
            ?: throw IllegalArgumentException("Unknown type: $typeName")
}