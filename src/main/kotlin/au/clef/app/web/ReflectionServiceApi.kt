package au.clef.app.web

import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.InvocationRequest
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodBinding
import au.clef.engine.model.Value

class ReflectionServiceApi(
    private val engine: ReflectionEngine,
    private val instanceRegistry: InstanceRegistry,
    private val valueMapper: ValueMapper
) {

    fun invoke(request: InvocationRequest): Any? {
        val className: String = request.methodId.substringBefore("#")
        val clazz: Class<*> = Class.forName(className)

        val binding: MethodBinding = engine.findBindingById(clazz, request.methodId)

        val instance: Any? =
            if (binding.descriptor.isStatic) {
                null
            } else {
                val targetId: String =
                    request.targetId ?: throw RuntimeException("Missing targetId")
                instanceRegistry.get(targetId)
            }

        val args: List<Value> = request.args.map { dto -> valueMapper.toEngineValue(dto) }

        return engine.invokeBinding(
            binding = binding,
            instance = instance,
            args = args
        )
    }
}