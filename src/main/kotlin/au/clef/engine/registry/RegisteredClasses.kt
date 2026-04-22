package au.clef.engine.registry

interface RegisteredClasses {
    fun classes(): List<Class<*>>
}