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

            return MethodId(
                "${declaringClass.name}$CLASS_NAME_SEPARATOR${method.name}($paramTypes)"
            )
        }

        fun from(declaringClass: KClass<*>, methodName: String, vararg params: KClass<*>): MethodId {
            val params: Array<Class<out Any>> = params.map { it.java }.toTypedArray()
            val  method: Method = declaringClass.java.getDeclaredMethod(methodName, *params)
            return from(method)
        }

        fun from(declaringClass: Class<*>, methodName: String, vararg parameterTypes: Class<*>): MethodId {
            val method: Method = declaringClass.getMethod(methodName, *parameterTypes)
            return from(method)
        }

        fun fromString(value: String): MethodId {
            require(value.isNotBlank()) { "MethodId cannot be blank" }
            require(value.contains(CLASS_NAME_SEPARATOR)) { "Invalid MethodId: missing '$CLASS_NAME_SEPARATOR'" }
            require(value.contains("(") && value.endsWith(")")) {
                "Invalid MethodId: expected methodName(paramTypes)"
            }
            val clazzName: String = value.substringBefore(CLASS_NAME_SEPARATOR)
            // todo just need to throw if class deosnt exists with the parameters
            val declaringClass: Class<*> = Class.forName(clazzName)
            return MethodId(value)
        }

    }
}

/**
 * JSON with:
 *
 * id
 * name
 * displayName
 * parameters
 * returnType
 * isStatic
 */
class MethodDescriptor(
    val id: MethodId, val method: Method, val displayName: String? = null, val parameters: List<ParamDescriptor>
) {
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