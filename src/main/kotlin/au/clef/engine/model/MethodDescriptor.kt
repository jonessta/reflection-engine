package au.clef.engine.model

import java.lang.reflect.Method
import java.lang.reflect.Modifier

class MethodId private constructor(val value: String, val declaringClass: Class<*>) {

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MethodId

        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    companion object {
        private const val CLASS_NAME_SEPARATOR: String = "#"

        fun fromMethod(method: Method): MethodId {
            val paramTypes: String = method.parameterTypes.joinToString(",") { it.name }
            val declaringClass: Class<*> = method.declaringClass

            return MethodId(
                "${declaringClass.name}$CLASS_NAME_SEPARATOR${method.name}($paramTypes)", declaringClass
            )
        }

        fun fromString(value: String): MethodId {
            require(value.isNotBlank()) { "MethodId cannot be blank" }
            require(value.contains(CLASS_NAME_SEPARATOR)) { "Invalid MethodId: missing '$CLASS_NAME_SEPARATOR'" }
            require(value.contains("(") && value.endsWith(")")) {
                "Invalid MethodId: expected methodName(paramTypes)"
            }
            val clazzName: String = value.substringBefore(CLASS_NAME_SEPARATOR)
            val declaringClass: Class<*> = Class.forName(clazzName)
            return MethodId(value, declaringClass)
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