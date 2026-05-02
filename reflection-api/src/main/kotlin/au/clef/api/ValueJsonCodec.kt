package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.Value
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

//
// todo Important caveat
// todo Your current Value.Scalar(val value: Any?) is still broad. That is okay short term, but the codec will become much easier to reason about if you later narrow what scalar payloads are allowed.

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
            is Value.Null -> JsonObject(
                mapOf("kind" to JsonPrimitive("null"))
            )

            is Value.Instance -> {
                throw IllegalArgumentException(
                    "Value.Instance is runtime-only and cannot be encoded to JSON"
                )
            }
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

    private fun encodeScalarValue(raw: Any?): JsonElement =
        when (raw) {
            null -> JsonNull
            is JsonElement -> raw
            is String -> JsonPrimitive(raw)
            is Int -> JsonPrimitive(raw)
            is Long -> JsonPrimitive(raw)
            is Double -> JsonPrimitive(raw)
            is Float -> JsonPrimitive(raw)
            is Short -> JsonPrimitive(raw.toInt())
            is Byte -> JsonPrimitive(raw.toInt())
            is Boolean -> JsonPrimitive(raw)
            is Char -> JsonPrimitive(raw.toString())
            is Enum<*> -> JsonPrimitive(raw.name)
            else -> {
                val converter: ScalarConverter<Any> =
                    scalarRegistry.encoderFor(raw)
                        ?: throw IllegalArgumentException("No scalar encoder for ${raw::class.java.name}")

                converter.encode(raw)
            }
        }

    private fun decodeScalar(obj: JsonObject): Value.Scalar {
        val jsonValue: JsonElement = obj["value"]
            ?: throw IllegalArgumentException("Missing 'value' for scalar")

        return Value.Scalar(
            when (jsonValue) {
                JsonNull -> null
                is JsonPrimitive -> {
                    if (jsonValue.isString) {
                        jsonValue.content
                    } else {
                        jsonValue.booleanOrNull
                            ?: jsonValue.longOrNull
                            ?: jsonValue.doubleOrNull
                            ?: jsonValue.content
                    }
                }
                else -> throw IllegalArgumentException("Scalar value must be a JSON primitive or null")
            }
        )
    }

    private fun encodeRecord(value: Value.Record): JsonElement =
        JsonObject(
            mapOf(
                "kind" to JsonPrimitive("record"),
                "type" to JsonPrimitive(value.type.name),
                "fields" to JsonObject(
                    value.fields.mapValues { (_, nested: Value) ->
                        encode(nested)
                    }
                )
            )
        )

    private fun decodeRecord(obj: JsonObject): Value.Record {
        val typeName: String = obj["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'type' for record")

        val resolved: ResolvedType = classResolver.resolve(typeName)
        require(resolved is ResolvedType.Structured) {
            "Type $typeName is scalar-like and cannot be a record"
        }

        val fieldsObject: JsonObject = obj["fields"] as? JsonObject
            ?: throw IllegalArgumentException("Missing or invalid 'fields' for record")

        return Value.Record(
            type = resolved.type,
            fields = fieldsObject.mapValues { (_, nested: JsonElement) ->
                decode(nested)
            }
        )
    }

    private fun encodeList(value: Value.ListValue): JsonElement =
        JsonObject(
            mapOf(
                "kind" to JsonPrimitive("list"),
                "items" to JsonArray(
                    value.items.map { item: Value ->
                        encode(item)
                    }
                )
            )
        )

    private fun decodeList(obj: JsonObject): Value.ListValue {
        val itemsArray: JsonArray = obj["items"] as? JsonArray
            ?: throw IllegalArgumentException("Missing or invalid 'items' for list")

        return Value.ListValue(
            items = itemsArray.map { item: JsonElement ->
                decode(item)
            }
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

                val keyJson: JsonElement = entryObject["key"]
                    ?: throw IllegalArgumentException("Map entry missing 'key'")

                val valueJson: JsonElement = entryObject["value"]
                    ?: throw IllegalArgumentException("Map entry missing 'value'")

                MapEntry(
                    key = decode(keyJson),
                    value = decode(valueJson)
                )
            }
        )
    }
}

