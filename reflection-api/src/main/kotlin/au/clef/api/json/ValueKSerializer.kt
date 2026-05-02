package au.clef.api.json

import au.clef.api.ClassResolver
import au.clef.api.ScalarTypeRegistry
import au.clef.api.model.Value
import au.clef.engine.ExecutionId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.modules.SerializersModule

class ValueKSerializer(
    private val codec: ValueJsonCodec
) : KSerializer<Value> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Value")

    override fun serialize(encoder: Encoder, value: Value) {
        val jsonEncoder: JsonEncoder = encoder as? JsonEncoder
            ?: error("ValueKSerializer only supports JSON")

        val element: JsonElement = codec.encode(value)
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Value {
        val jsonDecoder: JsonDecoder = decoder as? JsonDecoder
            ?: error("ValueKSerializer only supports JSON")

        val element: JsonElement = jsonDecoder.decodeJsonElement()
        return codec.decode(element)
    }
}

fun valueSerializersModule(
    classResolver: ClassResolver,
    scalarTypeRegistry: ScalarTypeRegistry
): SerializersModule {
    val codec = ValueJsonCodec(
        classResolver = classResolver,
        scalarRegistry = scalarTypeRegistry
    )

    return SerializersModule {
        contextual(ExecutionId::class, ExecutionIdSerializer)
        contextual(Value::class, ValueKSerializer(codec))
    }
}
