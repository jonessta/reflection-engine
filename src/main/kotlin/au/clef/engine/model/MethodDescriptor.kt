package au.clef.engine.model

import java.lang.reflect.Method
import java.lang.reflect.Modifier

class MethodDescriptor internal constructor(
    private val method: Method,
    val displayName: String? = null,
    val parameters: List<ParamDescriptor>
) {
    constructor(
        method: Method,
        displayName: String? = null
    ) : this(
        method,
        displayName,
        buildParamDescriptors(method)
    )

    init {
        require(parameters.size == method.parameterCount) {
            "Parameter descriptor count ${parameters.size} does not match method parameter count ${method.parameterCount} for ${method.name}"
        }
    }

    val id: MethodId = MethodId.from(method)

    val reflectedName: String get() = method.name

    val returnType: Class<*> get() = method.returnType

    val isStatic: Boolean get() = Modifier.isStatic(method.modifiers)

    fun withMetadata(
        displayName: String? = this.displayName,
        parameters: List<ParamDescriptor> = this.parameters
    ): MethodDescriptor = MethodDescriptor(method = method, displayName = displayName, parameters = parameters)

    override fun equals(other: Any?): Boolean = other is MethodDescriptor && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "MethodDescriptor(id=$id, displayName=$displayName, parameters=$parameters)"
}

data class ParamDescriptor(
    val index: Int,
    val type: Class<*>,
    val reflectedName: String,
    val name: String,
    val label: String? = null,
    val nullable: Boolean
)

private fun buildParamDescriptors(method: Method): List<ParamDescriptor> {
    val parameters = method.parameters
    return parameters.mapIndexed { index, parameter ->
        ParamDescriptor(
            index = index,
            type = parameter.type,
            reflectedName = parameter.name,
            name = parameter.name,
            label = null,
            nullable = !parameter.type.isPrimitive
        )
    }
}