package au.clef.api.json

import au.clef.engine.ExecutionId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ExecutionIdSerializer : KSerializer<ExecutionId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ExecutionId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ExecutionId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): ExecutionId =
        ExecutionId(decoder.decodeString())
}
