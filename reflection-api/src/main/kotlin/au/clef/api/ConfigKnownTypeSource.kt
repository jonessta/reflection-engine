package au.clef.api

import au.clef.engine.KnownTypeDiscoverer
import au.clef.engine.MethodSource
import au.clef.engine.ReflectionConfig
import au.clef.engine.TerminalTypeDecider
import au.clef.engine.registry.KnownTypeSource
import java.lang.reflect.Type

class ConfigKnownTypeSource(
    private val reflectionConfig: ReflectionConfig,
    private val terminalTypeDecider: TerminalTypeDecider
) : KnownTypeSource {

    override val declaringClasses: List<Class<*>> = reflectionConfig.methodSources
        .map { source: MethodSource -> source.declaringClass.java }
        .distinct()

    override val knownClasses: List<Class<*>> by lazy {
        val rootTypes: List<Type> = MethodTypeRoots(reflectionConfig).all()

        val discovered: Set<Class<*>> =
            KnownTypeDiscoverer(terminalTypeDecider).discover(rootTypes)

        val explicitAdditional: List<Class<*>> = reflectionConfig.additionalTypes.map { it.java }

        (declaringClasses + explicitAdditional + discovered).distinct()
    }
}