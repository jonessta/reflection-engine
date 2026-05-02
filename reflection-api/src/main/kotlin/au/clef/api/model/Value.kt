package au.clef.api.model

import au.clef.api.ClassResolver
import au.clef.api.ScalarTypeRegistry
import au.clef.api.ValueJsonCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.modules.SerializersModule


//todo  which file does this go in
// todo Configure serializers through the server’s Json instance and dependency wiring

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
        contextual(Value::class, ValueKSerializer(codec))
    }
}

sealed class Value {

    data class Scalar(
        val value: ScalarValue
    ) : Value()

    data class Record(
        val type: Class<*>,
        val fields: Map<String, Value>
    ) : Value()

    data class ListValue(
        val items: List<Value>
    ) : Value()

    data class MapValue(
        val entries: List<MapEntry>
    ) : Value()

    data object Null : Value()
}

data class MapEntry(
    val key: Value,
    val value: Value
)

sealed class ScalarValue {

    data class StringValue(
        val value: String
    ) : ScalarValue()

    data class BooleanValue(
        val value: Boolean
    ) : ScalarValue()

    /**
     * Canonical numeric scalar representation.
     * Keep the lexical form so later conversion can decide whether
     * the target should be Int, Long, Double, Float, etc.
     */
    data class NumberValue(
        val value: String
    ) : ScalarValue()
}