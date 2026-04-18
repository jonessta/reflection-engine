package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.InvocationRequest
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.Value

class ReflectionServiceApi(
    private val engine: ReflectionEngine,
    private val instanceRegistry: InstanceRegistry,
    private val valueMapper: ValueMapper
) {

    fun invoke(request: InvocationRequest): Any? {
        // todo this should be normalize in the api layer invoke should just take a MethodId
        val methodId: MethodId = MethodId.fromString(request.methodId)

        val descriptor: MethodDescriptor = engine.findDescriptorExact(methodId)

        val instance: Any? =
            if (descriptor.isStatic) {
                null
            } else {
                val targetId: String =
                    request.targetId ?: throw RuntimeException("Missing targetId")
                instanceRegistry.get(targetId)
            }

        val args: List<Value> =
            request.args.map { dto ->
                valueMapper.toEngineValue(dto)
            }

        return engine.invokeDescriptor(
            descriptor = descriptor,
            instance = instance,
            args = args
        )
    }
}