package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.ScalarValue
import au.clef.api.model.Value
import kotlinx.serialization.json.*

class ValueJsonCodec(
    private val classResolver: ClassResolver,
    private val scalarRegistry: ScalarTypeRegistry
) {

    fun encode(value: Value): JsonElement =
        when (value) {
            is Value.Scalar -> encodeScalar(value)
            is Value.Record -> encodeRecord(value)
            is Value.ListValue -> encodeList(value)
            is Value.MapValue -> encodeMap(value)
            is Value.Null -> JsonObject(mapOf("kind" to JsonPrimitive("null")))
        }

    fun decode(json: JsonElement): Value {
        val obj: JsonObject = json as? JsonObject
            ?: throw IllegalArgumentException("Expected JSON object for Value, got: $json")

        val kind: String = obj["kind"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'kind' field in Value JSON")

        return when (kind) {
            "scalar" -> decodeScalar(obj)
            "record" -> decodeRecord(obj)
            "list" -> decodeList(obj)
            "map" -> decodeMap(obj)
            "null" -> Value.Null
            else -> throw IllegalArgumentException("Unknown Value kind: $kind")
        }
    }

    private fun encodeScalar(value: Value.Scalar): JsonElement =
        JsonObject(
            mapOf(
                "kind" to JsonPrimitive("scalar"),
                "value" to encodeScalarValue(value.value)
            )
        )

    private fun encodeScalarValue(value: ScalarValue): JsonElement =
        when (value) {
            is ScalarValue.StringValue -> JsonPrimitive(value.value)
            is ScalarValue.BooleanValue -> JsonPrimitive(value.value)
            is ScalarValue.NumberValue -> {
                value.value.toLongOrNull()?.let { JsonPrimitive(it) }
                    ?: value.value.toDoubleOrNull()?.let { JsonPrimitive(it) }
                    ?: throw IllegalArgumentException("Invalid numeric scalar: ${value.value}")
            }
        }

    private fun decodeScalar(obj: JsonObject): Value.Scalar {
        val jsonValue: JsonElement =
            obj["value"] ?: throw IllegalArgumentException("Missing 'value' for scalar")

        val scalarValue: ScalarValue =
            when (jsonValue) {
                JsonNull -> throw IllegalArgumentException("Scalar value must not be null; use Value.Null")
                is JsonPrimitive -> {
                    if (jsonValue.isString) {
                        ScalarValue.StringValue(jsonValue.content)
                    } else {
                        jsonValue.booleanOrNull?.let { ScalarValue.BooleanValue(it) }
                            ?: jsonValue.longOrNull?.let { ScalarValue.NumberValue(it.toString()) }
                            ?: jsonValue.doubleOrNull?.let { ScalarValue.NumberValue(it.toString()) }
                            ?: ScalarValue.StringValue(jsonValue.content)
                    }
                }

                else -> throw IllegalArgumentException("Scalar value must be a JSON primitive")
            }

        return Value.Scalar(scalarValue)
    }

    private fun encodeRecord(value: Value.Record): JsonElement =
        JsonObject(
            mapOf(
                "kind" to JsonPrimitive("record"),
                "type" to JsonPrimitive(value.type.name),
                "fields" to JsonObject(
                    value.fields.mapValues { (_, nested: Value) -> encode(nested) }
                )
            )
        )

    private fun decodeRecord(obj: JsonObject): Value.Record {
        val typeName: String =
            obj["type"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing 'type' for record")

        val resolved: ResolvedType =
            try {
                classResolver.resolve(typeName)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Cannot decode record type '$typeName'. " +
                            "This type is not known to the API. " +
                            "If it is a structured model type, add it to reflectionConfig(...).supportingTypes(...).",
                    e
                )
            }

        require(resolved is ResolvedType.Structured) {
            "Type $typeName is scalar-like and cannot be a record"
        }

        val fieldsObject: JsonObject = obj["fields"] as? JsonObject
            ?: throw IllegalArgumentException("Missing or invalid 'fields' for record")

        return Value.Record(
            type = resolved.type,
            fields = fieldsObject.mapValues { (_, nested: JsonElement) -> decode(nested) }
        )
    }

    private fun encodeList(value: Value.ListValue): JsonElement =
        JsonObject(
            mapOf(
                "kind" to JsonPrimitive("list"),
                "items" to JsonArray(
                    value.items.map { item: Value -> encode(item) }
                )
            )
        )

    private fun decodeList(obj: JsonObject): Value.ListValue {
        val itemsArray: JsonArray = obj["items"] as? JsonArray
            ?: throw IllegalArgumentException("Missing or invalid 'items' for list")

        return Value.ListValue(
            items = itemsArray.map { item: JsonElement -> decode(item) }
        )
    }

    private fun encodeMap(value: Value.MapValue): JsonElement =
        JsonObject(
            mapOf(
                "kind" to JsonPrimitive("map"),
                "entries" to JsonArray(
                    value.entries.map { entry: MapEntry ->
                        JsonObject(
                            mapOf(
                                "key" to encode(entry.key),
                                "value" to encode(entry.value)
                            )
                        )
                    }
                )
            )
        )

    private fun decodeMap(obj: JsonObject): Value.MapValue {
        val entriesArray: JsonArray = obj["entries"] as? JsonArray
            ?: throw IllegalArgumentException("Missing or invalid 'entries' for map")

        return Value.MapValue(
            entries = entriesArray.map { entryElement: JsonElement ->
                val entryObject: JsonObject = entryElement as? JsonObject
                    ?: throw IllegalArgumentException("Map entry must be an object")

                val keyJson: JsonElement =
                    entryObject["key"] ?: throw IllegalArgumentException("Map entry missing 'key'")

                val valueJson: JsonElement =
                    entryObject["value"] ?: throw IllegalArgumentException("Map entry missing 'value'")

                MapEntry(
                    key = decode(keyJson),
                    value = decode(valueJson)
                )
            }
        )
    }
}