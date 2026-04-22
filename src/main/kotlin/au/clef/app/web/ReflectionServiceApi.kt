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
        val clazz = classResolver.resolve(typeName)
        return engine.descriptors(clazz)
    }

    fun invoke(request: InvocationRequest): Any? {
        val methodId = MethodId.fromValue(request.methodId)
        val args = request.args.map(valueMapper::toEngineValue)

        return if (request.targetId != null) {
            val instance = instanceRegistry.get(request.targetId)
            engine.invoke(methodId, instance, args)
        } else {
            engine.invoke(methodId, args)
        }
    }
}