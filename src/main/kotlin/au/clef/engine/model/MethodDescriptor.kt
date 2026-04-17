package au.clef.engine.model

import java.lang.reflect.Method
import java.lang.reflect.Modifier


@JvmInline
value class MethodId private constructor(
    val value: String
) {
    override fun toString(): String = value

    fun resolveMethod(): Method {
        val className: String = value.substringBefore(CLASS_NAME_SEPARATOR)
        val methodPart: String = value.substringAfter(CLASS_NAME_SEPARATOR)
        val methodName: String = methodPart.substringBefore("(")
        val paramsRaw: String = methodPart.substringAfter("(").substringBeforeLast(")")

        val paramTypes: Array<Class<*>> =
            if (paramsRaw.isBlank()) {
                emptyArray()
            } else {
                paramsRaw.split(",")
                    .map { resolveType(it.trim()) }
                    .toTypedArray()
            }

        val clazz: Class<*> = Class.forName(className)

        return clazz.getMethod(methodName, *paramTypes)
    }

    companion object {
        private const val CLASS_NAME_SEPARATOR: String = "#"

        fun fromMethod(method: Method): MethodId {
            val paramTypes: String = method.parameterTypes.joinToString(",") { it.name }
            return MethodId("${method.declaringClass.name}$CLASS_NAME_SEPARATOR${method.name}($paramTypes)")
        }

        fun fromString(value: String): MethodId {
            require(value.isNotBlank()) { "MethodId cannot be blank" }
            require(value.contains(CLASS_NAME_SEPARATOR)) { "Invalid MethodId: missing '$CLASS_NAME_SEPARATOR'" }
            require(value.contains("(") && value.endsWith(")")) {
                "Invalid MethodId: expected methodName(paramTypes)"
            }
            return MethodId(value)
        }

        private fun resolveType(typeName: String): Class<*> =
            when (typeName) {
                "int" -> Int::class.javaPrimitiveType!!
                "long" -> Long::class.javaPrimitiveType!!
                "double" -> Double::class.javaPrimitiveType!!
                "float" -> Float::class.javaPrimitiveType!!
                "boolean" -> Boolean::class.javaPrimitiveType!!
                "short" -> Short::class.javaPrimitiveType!!
                "byte" -> Byte::class.javaPrimitiveType!!
                "char" -> Char::class.javaPrimitiveType!!
                "void" -> Void.TYPE
                else -> Class.forName(typeName)
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
data class MethodDescriptor(
    val id: MethodId,
    val method: Method,
    val displayName: String? = null,
    val parameters: List<ParamDescriptor>
) {
    val name: String get() = method.name

    val returnType: Class<*> get() = method.returnType

    val isStatic: Boolean get() = Modifier.isStatic(method.modifiers)
}

data class ParamDescriptor(
    val index: Int,
    val type: Class<*>,
    val rawName: String,
    val name: String,
    val label: String? = null,
    val nullable: Boolean
)