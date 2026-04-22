package au.clef.app.web

import au.clef.api.ClassResolver
import au.clef.api.InstanceRegistry
import au.clef.api.ValueMapper
import au.clef.api.model.InvocationRequest
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodId

class ReflectionServiceApi(
    classes: List<Class<*>>,
    private val engine: ReflectionEngine,
    private val instanceRegistry: InstanceRegistry
) : ClassResolver {

    private val classesByName: Map<String, Class<*>> =
        classes.flatMap { clazz ->
            listOf(
                clazz.name to clazz,
                clazz.simpleName to clazz
            )
        }.toMap()

    private val valueMapper = ValueMapper(instanceRegistry, this)

    override fun resolve(typeName: String): Class<*> =
        classesByName[typeName]
            ?: throw IllegalArgumentException("Unknown type: $typeName")

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