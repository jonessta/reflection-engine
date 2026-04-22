package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.InvocationRequest
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId

class ReflectionServiceApi(
    private val engine: ReflectionEngine,
    private val instanceRegistry: InstanceRegistry
) {
    private val classResolver = DefaultClassResolver(registeredClasses = engine)

    private val valueMapper = ValueMapper(
        instanceRegistry,
        classResolver
    )

    fun descriptors(typeName: String): List<MethodDescriptor> {
        val clazz: Class<*> = classResolver.resolve(typeName)
        return engine.descriptors(clazz)
    }

    fun invoke(request: InvocationRequest): Any? {
        val methodId = MethodId.fromValue(request.methodId)
        val descriptor = engine.descriptor(methodId)
        val args = request.args.map(valueMapper::toEngineValue)

        return if (descriptor.isStatic) {
            if (request.instanceId != null) {
                throw IllegalArgumentException(
                    "Method ${descriptor.id.value} is static and must not specify targetId"
                )
            }
            engine.invokeStatic(descriptor, args)
        } else {
            val instanceId = request.instanceId
                ?: throw IllegalArgumentException(
                    "Method ${descriptor.id.value} is an instance method and requires targetId"
                )

            val instance = instanceRegistry.get(instanceId)
            engine.invokeInstance(descriptor, instance, args)
        }
    }
}