package au.clef.engine.registry

interface ReflectionTypes {

    /**
     * Classes whose methods are directly exposed for invocation.
     */
    val targetClasses: List<Class<*>>

    /**
     * All classes known to the reflection runtime, including target classes.
     */
    val classes: List<Class<*>>
}