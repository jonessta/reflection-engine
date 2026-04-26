package au.clef.engine.registry

interface ReflectionTypes {

    /**
     * Classes whose methods are directly exposed for invocation.
     */
    val declaringClasses: List<Class<*>>

    /**
     * All classes known to the reflection runtime, including declaringClasses.
     */
    val classes: List<Class<*>>
}