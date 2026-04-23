package au.clef.engine.model

import au.clef.engine.EngineException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

class IllegalMethodIdException(msg: String) : EngineException("Invalid MethodId: $msg")

@Serializable(with = MethodIdSerializer::class)
data class MethodId private constructor(
    val declaringClassName: String,
    val methodName: String,
    val parameterTypeNames: List<String>
) {
    val value: String =
        buildString {
            append(declaringClassName)
            append(CLASS_NAME_SEPARATOR)
            append(methodName)
            append("(")
            append(parameterTypeNames.joinToString(","))
            append(")")
        }

    override fun toString(): String = value

    companion object {
        internal const val CLASS_NAME_SEPARATOR: String = "#"

        private val METHOD_ID_OUTER_REGEX = Regex(
            """^([A-Za-z_][A-Za-z0-9_$.]*)$CLASS_NAME_SEPARATOR([A-Za-z_][A-Za-z0-9_$]*)\((.*)\)$"""
        )

        private val TYPE_NAME_REGEX = Regex("""^[A-Za-z_][A-Za-z0-9_$.]*$""")
        private val METHOD_NAME_REGEX = Regex("""^[A-Za-z_][A-Za-z0-9_$]*$""")
        private val CLASS_NAME_REGEX = Regex("""^[A-Za-z_][A-Za-z0-9_$.]*$""")

        fun from(method: Method): MethodId =
            MethodId(
                declaringClassName = method.declaringClass.name,
                methodName = method.name,
                parameterTypeNames = method.parameterTypes.map { it.name }
            )

        fun from(
            declaringClass: KClass<*>,
            methodName: String,
            vararg parameterTypes: KClass<*>
        ): MethodId {
            val paramTypes = parameterTypes.map { it.java }.toTypedArray()
            val method = declaringClass.java.getDeclaredMethod(methodName, *paramTypes)
            return from(method)
        }

        fun fromValue(value: String): MethodId {
            val match = METHOD_ID_OUTER_REGEX.matchEntire(value)
                ?: throw IllegalMethodIdException("expected <class>#<method>(<paramTypes>)")

            val declaringClassName = match.groupValues[1]
            val methodName = match.groupValues[2]
            val paramsPart = match.groupValues[3]

            val parameterTypeNames =
                if (paramsPart.isBlank()) {
                    emptyList()
                } else {
                    paramsPart.split(",").also { paramTypes ->
                        if (paramTypes.any { it.isBlank() }) {
                            throw IllegalMethodIdException(
                                "parameter types must be comma-separated with no empty entries"
                            )
                        }
                        if (!paramTypes.all { TYPE_NAME_REGEX.matches(it) }) {
                            throw IllegalMethodIdException("parameter type names are malformed")
                        }
                    }
                }

            return of(declaringClassName, methodName, parameterTypeNames)
        }

        fun of(
            declaringClassName: String,
            methodName: String,
            parameterTypeNames: List<String> = emptyList()
        ): MethodId {
            if (!CLASS_NAME_REGEX.matches(declaringClassName)) {
                throw IllegalMethodIdException("declaring class name is malformed: $declaringClassName")
            }
            if (!METHOD_NAME_REGEX.matches(methodName)) {
                throw IllegalMethodIdException("method name is malformed: $methodName")
            }
            if (!parameterTypeNames.all { TYPE_NAME_REGEX.matches(it) }) {
                throw IllegalMethodIdException("parameter type names are malformed")
            }

            return MethodId(
                declaringClassName = declaringClassName,
                methodName = methodName,
                parameterTypeNames = parameterTypeNames
            )
        }
    }
}

object MethodIdSerializer : KSerializer<MethodId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MethodId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MethodId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): MethodId {
        return MethodId.fromValue(decoder.decodeString())
    }
}

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

class MethodDescriptor(
    val method: Method,
    val displayName: String? = null,
    val parameters: List<ParamDescriptor>
) {
    constructor(
        method: Method,
        displayName: String? = null
    ) : this(method, displayName, buildParamDescriptors(method))

    val id: MethodId = MethodId.from(method)

    val reflectedName: String get() = method.name
    val returnType: Class<*> get() = method.returnType
    val isStatic: Boolean get() = Modifier.isStatic(method.modifiers)

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