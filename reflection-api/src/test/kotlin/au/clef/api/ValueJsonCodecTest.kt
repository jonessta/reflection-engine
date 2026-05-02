package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.ScalarValue
import au.clef.api.model.Value
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValueJsonCodecTest {

    private val codec: ValueJsonCodec = ValueJsonCodec(
        classResolver = FakeClassResolver().apply {
            putStructured(Person::class.java.name, Person::class.java)
            putStructured(Address3::class.java.name, Address3::class.java)
        },
        scalarRegistry = ScalarTypeRegistry(
            userDefinedConverters = listOf(
                scalarConverter<CustomerId1>(
                    encode = { value: CustomerId1 ->
                        ScalarValue.StringValue(value.value)
                    },
                    decode = { value: ScalarValue ->
                        when (value) {
                            is ScalarValue.StringValue -> CustomerId1(value.value)
                            else -> throw IllegalArgumentException("Expected string scalar for CustomerId")
                        }
                    }
                )
            )
        )
    )

    @Test
    fun encodeDecode_stringScalar_roundTrips() {
        val value: Value = Value.Scalar(ScalarValue.StringValue("abc"))

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val scalar: Value.Scalar = assertIs(decoded)
        assertEquals(ScalarValue.StringValue("abc"), scalar.value)
    }

    @Test
    fun encodeDecode_intScalar_roundTripsAsNumber() {
        val value: Value = Value.Scalar(ScalarValue.NumberValue("123"))

        val json = codec.encode(value)
        val obj: JsonObject = assertIs(json)
        assertEquals(JsonPrimitive("scalar"), obj["kind"])
        assertEquals(JsonPrimitive(123), obj["value"])

        val decoded: Value = codec.decode(json)
        val scalar: Value.Scalar = assertIs(decoded)
        assertEquals(ScalarValue.NumberValue("123"), scalar.value)
    }

    @Test
    fun encodeDecode_booleanScalar_roundTrips() {
        val value: Value = Value.Scalar(ScalarValue.BooleanValue(true))

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val scalar: Value.Scalar = assertIs(decoded)
        assertEquals(ScalarValue.BooleanValue(true), scalar.value)
    }

    @Test
    fun encodeDecode_customScalar_roundTrips() {
        val value: Value = Value.Scalar(ScalarValue.StringValue("cust-123"))

        val json = codec.encode(value)
        val obj: JsonObject = assertIs(json)
        assertEquals(JsonPrimitive("scalar"), obj["kind"])
        assertEquals(JsonPrimitive("cust-123"), obj["value"])

        val decoded: Value = codec.decode(json)
        val scalar: Value.Scalar = assertIs(decoded)
        assertEquals(ScalarValue.StringValue("cust-123"), scalar.value)
    }

    @Test
    fun encodeDecode_nullValue_roundTrips() {
        val json = codec.encode(Value.Null)
        val decoded: Value = codec.decode(json)

        assertEquals(Value.Null, decoded)
    }

    @Test
    fun encodeDecode_list_roundTrips() {
        val value: Value = Value.ListValue(
            items = listOf(
                Value.Scalar(ScalarValue.StringValue("a")),
                Value.Scalar(ScalarValue.NumberValue("2")),
                Value.Null
            )
        )

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val list: Value.ListValue = assertIs(decoded)
        assertEquals(3, list.items.size)
        assertEquals(ScalarValue.StringValue("a"), assertIs<Value.Scalar>(list.items[0]).value)
        assertEquals(ScalarValue.NumberValue("2"), assertIs<Value.Scalar>(list.items[1]).value)
        assertEquals(Value.Null, list.items[2])
    }

    @Test
    fun encodeDecode_record_roundTrips() {
        val value: Value = Value.Record(
            type = Person::class.java,
            fields = mapOf(
                "name" to Value.Scalar(ScalarValue.StringValue("Alice")),
                "age" to Value.Scalar(ScalarValue.NumberValue("41")),
                "address" to Value.Record(
                    type = Address3::class.java,
                    fields = mapOf(
                        "street" to Value.Scalar(ScalarValue.StringValue("Smith St"))
                    )
                )
            )
        )

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val record: Value.Record = assertIs(decoded)
        assertEquals(Person::class.java, record.type)
        assertEquals(
            ScalarValue.StringValue("Alice"),
            assertIs<Value.Scalar>(record.fields.getValue("name")).value
        )
        assertEquals(
            ScalarValue.NumberValue("41"),
            assertIs<Value.Scalar>(record.fields.getValue("age")).value
        )

        val address: Value.Record = assertIs(record.fields.getValue("address"))
        assertEquals(Address3::class.java, address.type)
        assertEquals(
            ScalarValue.StringValue("Smith St"),
            assertIs<Value.Scalar>(address.fields.getValue("street")).value
        )
    }

    @Test
    fun encodeDecode_map_roundTrips() {
        val value: Value = Value.MapValue(
            entries = listOf(
                MapEntry(
                    key = Value.Scalar(ScalarValue.StringValue("one")),
                    value = Value.Scalar(ScalarValue.NumberValue("1"))
                ),
                MapEntry(
                    key = Value.Scalar(ScalarValue.NumberValue("2")),
                    value = Value.ListValue(
                        items = listOf(
                            Value.Scalar(ScalarValue.StringValue("two"))
                        )
                    )
                )
            )
        )

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val map: Value.MapValue = assertIs(decoded)
        assertEquals(2, map.entries.size)

        val first: MapEntry = map.entries[0]
        assertEquals(
            ScalarValue.StringValue("one"),
            assertIs<Value.Scalar>(first.key).value
        )
        assertEquals(
            ScalarValue.NumberValue("1"),
            assertIs<Value.Scalar>(first.value).value
        )

        val second: MapEntry = map.entries[1]
        assertEquals(
            ScalarValue.NumberValue("2"),
            assertIs<Value.Scalar>(second.key).value
        )
        val nestedList: Value.ListValue = assertIs(second.value)
        assertEquals(
            ScalarValue.StringValue("two"),
            assertIs<Value.Scalar>(nestedList.items[0]).value
        )
    }

    @Test
    fun decode_rejects_recordWhoseTypeResolvesToScalar() {
        val scalarResolvingCodec: ValueJsonCodec = ValueJsonCodec(
            classResolver = FakeClassResolver().apply {
                putScalar(String::class.java.name, String::class.java)
            },
            scalarRegistry = ScalarTypeRegistry()
        )

        val json = JsonObject(
            mapOf(
                "kind" to JsonPrimitive("record"),
                "type" to JsonPrimitive(String::class.java.name),
                "fields" to JsonObject(emptyMap())
            )
        )

        val ex: IllegalArgumentException = assertFailsWith {
            scalarResolvingCodec.decode(json)
        }

        assertTrue(ex.message!!.contains("scalar-like"))
    }

    @Test
    fun decode_rejects_missingKind() {
        val json = JsonObject(
            mapOf(
                "value" to JsonPrimitive("x")
            )
        )

        val ex: IllegalArgumentException = assertFailsWith {
            codec.decode(json)
        }

        assertTrue(ex.message!!.contains("Missing 'kind'"))
    }

    @Test
    fun decode_rejects_unknownKind() {
        val json = JsonObject(
            mapOf(
                "kind" to JsonPrimitive("mystery")
            )
        )

        val ex: IllegalArgumentException = assertFailsWith {
            codec.decode(json)
        }

        assertTrue(ex.message!!.contains("Unknown Value kind"))
    }
}

private class FakeClassResolver : ClassResolver {

    private val values: MutableMap<String, ResolvedType> = linkedMapOf()

    fun putScalar(typeName: String, clazz: Class<*>) {
        values[typeName] = ResolvedType.Scalar(clazz)
    }

    fun putStructured(typeName: String, clazz: Class<*>) {
        values[typeName] = ResolvedType.Structured(clazz)
    }

    override fun resolve(typeName: String): ResolvedType =
        values[typeName] ?: throw IllegalArgumentException("Unknown type: $typeName")
}

@JvmInline
value class CustomerId(val value: String)

class Person
class Address