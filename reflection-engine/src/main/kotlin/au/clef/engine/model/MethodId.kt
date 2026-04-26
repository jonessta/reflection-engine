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

private const val CLASS_NAME_SEPARATOR = "#"

private fun formatMethodId(declaringClassName: String, methodName: String, parameterTypeNames: List<String>): String =
    buildString {
        append(declaringClassName)
        append(CLASS_NAME_SEPARATOR)
        append(methodName)
        append("(")
        append(parameterTypeNames.joinToString(","))
        append(")")
    }

@Serializable(with = MethodIdSerializer::class)
class MethodId private constructor(val value: String) {

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is MethodId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    companion object {

        private val METHOD_ID_OUTER_REGEX = Regex(
            """^([A-Za-z_][A-Za-z0-9_$.]*)$CLASS_NAME_SEPARATOR([A-Za-z_][A-Za-z0-9_$]*)\((.*)\)$"""
        )

        private val TYPE_NAME_REGEX = Regex("""^[A-Za-z_][A-Za-z0-9_$.]*$""")

        fun from(method: Method): MethodId =
            MethodId(
                formatMethodId(
                    method.declaringClass.name,
                    method.name,
                    method.parameterTypes.map { it.name }
                )
            )

        fun from(declaringClass: KClass<*>, methodName: String, vararg parameterTypes: KClass<*>): MethodId {
            try {
                val method: Method = declaringClass.java.getMethod(
                    methodName,
                    *parameterTypes.map { it.java }.toTypedArray()
                )
                return from(method)
            } catch (_: NoSuchMethodException) {
                throw IllegalMethodIdException(
                    "No public method '$methodName' in ${declaringClass.java.name} with parameters ${parameterTypes.joinToString { it.java.name }}"
                )
            }
        }

        internal fun fromValue(value: String): MethodId {
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
                        if (paramTypes.any(String::isBlank)) {
                            throw IllegalMethodIdException(
                                "parameter types must be comma-separated with no empty entries"
                            )
                        }
                        if (!paramTypes.all(TYPE_NAME_REGEX::matches)) {
                            throw IllegalMethodIdException("parameter type names are malformed")
                        }
                    }
                }

            // todo you could put a debug guard and create the Method to se if its value?
            return MethodId(
                formatMethodId(declaringClassName, methodName, parameterTypeNames)
            )
        }

    }
}

object MethodIdSerializer : KSerializer<MethodId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MethodId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: MethodId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder): MethodId = MethodId.fromValue(decoder.decodeString())
}