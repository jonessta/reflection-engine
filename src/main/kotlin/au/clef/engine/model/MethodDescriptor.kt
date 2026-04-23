package au.clef.engine.model

import java.lang.reflect.Method
import java.lang.reflect.Modifier

class MethodDescriptor(
    val id: MethodId,
    val declaringClass: Class<*>,
    val reflectedName: String,
    val displayName: String? = null,
    val parameters: List<ParamDescriptor>,
    val returnType: Class<*>,
    val isStatic: Boolean
) {

    companion object {
        fun from(method: Method, displayName: String? = null): MethodDescriptor =
            MethodDescriptor(
                id = MethodId.from(method),
                declaringClass = method.declaringClass,
                reflectedName = method.name,
                displayName = displayName,
                parameters = buildParamDescriptors(method),
                returnType = method.returnType,
                isStatic = Modifier.isStatic(method.modifiers)
            )
    }

    fun withMetadata(
        displayName: String? = this.displayName,
        parameters: List<ParamDescriptor> = this.parameters
    ): MethodDescriptor =
        MethodDescriptor(
            id,
            declaringClass,
            reflectedName,
            displayName = displayName,
            parameters = parameters,
            returnType,
            isStatic
        )

    override fun equals(other: Any?): Boolean = other is MethodDescriptor && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "MethodDescriptor(id=$id, displayName=$displayName, parameters=$parameters)"
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