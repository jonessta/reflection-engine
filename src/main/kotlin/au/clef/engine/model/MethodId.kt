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
import kotlin.reflect.KClass

class IllegalMethodIdException(msg: String) : EngineException("Invalid MethodId: $msg")

@Serializable(with = MethodIdSerializer::class)
class MethodId private constructor(
    val declaringClassName: String,
    val methodName: String,
    val parameterTypeNames: List<String>
) {

    val value: String = buildString {
        append(declaringClassName)
        append(CLASS_NAME_SEPARATOR)
        append(methodName)
        append("(")
        append(parameterTypeNames.joinToString(","))
        append(")")
    }

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean =
        other is MethodId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        private const val CLASS_NAME_SEPARATOR: String = "#"

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

        fun from(declaringClass: KClass<*>, methodName: String, vararg parameterTypes: KClass<*>): MethodId {
            val method: Method = declaringClass.java.getDeclaredMethod(
                methodName,
                *parameterTypes.map { it.java }.toTypedArray()
            )
            return from(method)
        }

        fun fromValue(value: String): MethodId {
            val match: MatchResult = METHOD_ID_OUTER_REGEX.matchEntire(value)
                ?: throw IllegalMethodIdException("expected <class>#<method>(<paramTypes>)")

            val declaringClassName = match.groupValues[1]
            val methodName = match.groupValues[2]
            val paramsPart = match.groupValues[3]

            if (!CLASS_NAME_REGEX.matches(declaringClassName)) {
                throw IllegalMethodIdException("declaring class name is malformed: $declaringClassName")
            }

            if (!METHOD_NAME_REGEX.matches(methodName)) {
                throw IllegalMethodIdException("method name is malformed: $methodName")
            }

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

            return MethodId(declaringClassName, methodName, parameterTypeNames)
        }
    }
}

object MethodIdSerializer : KSerializer<MethodId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MethodId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: MethodId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): MethodId = MethodId.fromValue(decoder.decodeString())
}