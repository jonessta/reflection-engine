package au.clef.engine.registry

interface MethodSourceTypes {

    /**
     * Classes whose methods are directly exposed for invocation.
     */
    val declaringClasses: List<Class<*>>

    /**
     * All classes known to the reflection runtime, including declaringClasses.
     */
    val knownClasses: List<Class<*>>
}