package au.clef.engine.model

import kotlinx.serialization.Serializable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

@Serializable
@JvmInline
value class MethodId private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        private const val CLASS_NAME_SEPARATOR: String = "#"

        fun from(method: Method): MethodId {
            val paramTypes: String = method.parameterTypes.joinToString(",") { it.name }
            val declaringClass: Class<*> = method.declaringClass
            return MethodId("${declaringClass.name}$CLASS_NAME_SEPARATOR${method.name}($paramTypes)")
        }

        fun from(declaringClass: KClass<*>, methodName: String, vararg parameterTypes: KClass<*>): MethodId {
            val paramTypes: Array<Class<*>> = parameterTypes.map { it.java }.toTypedArray()
            val method: Method = declaringClass.java.getDeclaredMethod(methodName, *paramTypes)
            return from(method)
        }

        fun fromValue(idString: String): MethodId {
            require(idString.isNotBlank()) { "MethodId cannot be blank" }
            require(idString.contains(CLASS_NAME_SEPARATOR)) { "Invalid MethodId: missing '$CLASS_NAME_SEPARATOR'" }
            require(idString.contains("(") && idString.endsWith(")")) { "Invalid MethodId: expected methodName(paramTypes)" }
            val clazzName: String = idString.substringBefore(CLASS_NAME_SEPARATOR)
            // todo just need to throw if class deosnt exists with the parameters
            Class.forName(clazzName)
            return MethodId(idString)
        }
    }
}

private fun buildParamDescriptors(method: Method): List<ParamDescriptor> {
    val parameters: Array<java.lang.reflect.Parameter> = method.parameters
    return parameters.mapIndexed { index: Int, parameter: java.lang.reflect.Parameter ->
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

class MethodDescriptor(
    val method: Method,
    val displayName: String? = null,
    val parameters: List<ParamDescriptor>
) {

    constructor(
        method: Method,
        displayName: String? = null
    ) : this(method, displayName, buildParamDescriptors(method))

    val id: MethodId get() = MethodId.from(method)

    val reflectedName: String get() = method.name

    val returnType: Class<*> get() = method.returnType

    val isStatic: Boolean get() = Modifier.isStatic(method.modifiers)

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