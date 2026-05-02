package au.clef.engine

import au.clef.engine.model.MethodId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

// todo should I move
object ExecutionIdSerializer : KSerializer<ExecutionId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ExecutionId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ExecutionId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): ExecutionId =
        ExecutionId(decoder.decodeString())
}


@Serializable(with = ExecutionIdSerializer::class)
@JvmInline
value class ExecutionId(val value: String) {
    override fun toString(): String = value
}

sealed class ExecutionContext(
    open val methodId: MethodId
) {
    abstract val executionId: ExecutionId

    data class Static(
        override val methodId: MethodId
    ) : ExecutionContext(methodId) {
        override val executionId: ExecutionId =
            ExecutionId("static:${methodId}")
    }

    data class Instance(
        val instance: Any,
        val instanceDescription: String,
        override val methodId: MethodId
    ) : ExecutionContext(methodId) {
        override val executionId: ExecutionId =
            ExecutionId("instance:${UUID.randomUUID()}:${methodId}")
    }
}