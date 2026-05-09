package au.clef.api

import au.clef.engine.KnownTypeDiscoverer
import au.clef.engine.ReflectionConfig
import au.clef.engine.TerminalTypeDecider
import au.clef.engine.registry.KnownTypeSource
import au.clef.engine.registry.MethodSourceRegistry
import java.lang.reflect.Type

class ConfigKnownTypeSource(
    private val reflectionConfig: ReflectionConfig,
    private val terminalTypeDecider: TerminalTypeDecider
) : KnownTypeSource {

    private val methodSourceRegistry: MethodSourceRegistry =
        MethodSourceRegistry(reflectionConfig)

    override val declaringClasses: List<Class<*>> =
        methodSourceRegistry.declaringClasses

    override val knownClasses: List<Class<*>> by lazy {
        val rootTypes: List<Type> = MethodTypeRoots(methodSourceRegistry).all()

        val discovered: Set<Class<*>> =
            KnownTypeDiscoverer(terminalTypeDecider).discover(rootTypes)

        val explicitAdditional: List<Class<*>> =
            reflectionConfig.additionalTypes.map { it.java }

        (declaringClasses + explicitAdditional + discovered).distinct()
    }
}