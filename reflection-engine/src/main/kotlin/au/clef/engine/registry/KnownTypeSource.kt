package au.clef.engine.registry

interface KnownTypeSource {

    /**
     * Classes whose methods are directly exposed for invocation.
     */
    val declaringClasses: List<Class<*>>

    /**
     * All classes known to the reflection runtime, including declaringClasses.
     */
    val knownClasses: List<Class<*>>
}