package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.InvocationRequest
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.*

class ReflectionServiceApi(
    private val engine: ReflectionEngine,
    private val instanceRegistry: InstanceRegistry,
    private val valueMapper: ValueMapper
) {

    fun invoke(request: InvocationRequest): Any? {
        val methodId: MethodId = MethodId.fromString(request.methodId)
        val descriptor: MethodDescriptor = engine.findDescriptorExact(methodId)

        val instance: Any? = if (descriptor.isStatic) {
            null
        } else {
            val targetId: String = request.targetId ?: throw RuntimeException("Missing targetId for instance method")
            instanceRegistry.get(targetId)
        }

        val args: List<Value> = request.args.map { dto ->
            valueMapper.toEngineValue(dto)
        }

        return engine.invokeDescriptor(
            descriptor, instance, args,
        )
    }
}