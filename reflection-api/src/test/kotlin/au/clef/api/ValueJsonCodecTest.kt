package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.Value
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class ValueJsonCodecTest {

    private val codec: ValueJsonCodec = ValueJsonCodec(
        classResolver = FakeClassResolver().apply {
            putStructured(Person::class.java.name, Person::class.java)
            putStructured(Address3::class.java.name, Address3::class.java)
        },
        scalarRegistry = ScalarTypeRegistry(
            userDefinedConverters = listOf(
                scalarConverter<CustomerId1>(
                    encode = { JsonPrimitive(it.value) },
                    decode = { CustomerId1(it) }
                )
            )
        )
    )

    @Test
    fun encodeDecode_stringScalar_roundTrips() {
        val value: Value = Value.Scalar("abc")

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val scalar: Value.Scalar = assertIs(decoded)
        assertEquals("abc", scalar.value)
    }

    @Test
    fun encodeDecode_intScalar_roundTripsAsNumber() {
        val value: Value = Value.Scalar(123)

        val json = codec.encode(value)
        val obj: JsonObject = assertIs(json)
        assertEquals(JsonPrimitive("scalar"), obj["kind"])
        assertEquals(JsonPrimitive(123), obj["value"])

        val decoded: Value = codec.decode(json)
        val scalar: Value.Scalar = assertIs(decoded)
        assertEquals(123L, scalar.value)
    }

    @Test
    fun encodeDecode_booleanScalar_roundTrips() {
        val value: Value = Value.Scalar(true)

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val scalar: Value.Scalar = assertIs(decoded)
        assertEquals(true, scalar.value)
    }

    @Test
    fun encodeDecode_nullScalar_roundTrips() {
        val value: Value = Value.Scalar(null)

        val json = codec.encode(value)
        val obj: JsonObject = assertIs(json)
        assertEquals(JsonPrimitive("scalar"), obj["kind"])
        assertEquals(JsonNull, obj["value"])

        val decoded: Value = codec.decode(json)
        val scalar: Value.Scalar = assertIs(decoded)
        assertNull(scalar.value)
    }

    @Test
    fun encodeDecode_customScalar_roundTrips() {
        val value: Value = Value.Scalar(CustomerId1("cust-123"))

        val json = codec.encode(value)
        val obj: JsonObject = assertIs(json)
        assertEquals(JsonPrimitive("scalar"), obj["kind"])
        assertEquals(JsonPrimitive("cust-123"), obj["value"])

        val decoded: Value = codec.decode(json)
        val scalar: Value.Scalar = assertIs(decoded)
        assertEquals("cust-123", scalar.value)
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
                Value.Scalar("a"),
                Value.Scalar(2),
                Value.Null
            )
        )

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val list: Value.ListValue = assertIs(decoded)
        assertEquals(3, list.items.size)
        assertEquals("a", assertIs<Value.Scalar>(list.items[0]).value)
        assertEquals(2L, assertIs<Value.Scalar>(list.items[1]).value)
        assertEquals(Value.Null, list.items[2])
    }

    @Test
    fun encodeDecode_record_roundTrips() {
        val value: Value = Value.Record(
            type = Person::class.java,
            fields = mapOf(
                "name" to Value.Scalar("Alice"),
                "age" to Value.Scalar(41),
                "address" to Value.Record(
                    type = Address3::class.java,
                    fields = mapOf(
                        "street" to Value.Scalar("Smith St")
                    )
                )
            )
        )

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val record: Value.Record = assertIs(decoded)
        assertEquals(Person::class.java, record.type)
        assertEquals("Alice", assertIs<Value.Scalar>(record.fields.getValue("name")).value)
        assertEquals(41L, assertIs<Value.Scalar>(record.fields.getValue("age")).value)

        val address: Value.Record = assertIs(record.fields.getValue("address"))
        assertEquals(Address3::class.java, address.type)
        assertEquals("Smith St", assertIs<Value.Scalar>(address.fields.getValue("street")).value)
    }

    @Test
    fun encodeDecode_map_roundTrips() {
        val value: Value = Value.MapValue(
            entries = listOf(
                MapEntry(
                    key = Value.Scalar("one"),
                    value = Value.Scalar(1)
                ),
                MapEntry(
                    key = Value.Scalar(2),
                    value = Value.ListValue(
                        items = listOf(Value.Scalar("two"))
                    )
                )
            )
        )

        val json = codec.encode(value)
        val decoded: Value = codec.decode(json)

        val map: Value.MapValue = assertIs(decoded)
        assertEquals(2, map.entries.size)

        val first: MapEntry = map.entries[0]
        assertEquals("one", assertIs<Value.Scalar>(first.key).value)
        assertEquals(1L, assertIs<Value.Scalar>(first.value).value)

        val second: MapEntry = map.entries[1]
        assertEquals(2L, assertIs<Value.Scalar>(second.key).value)
        val nestedList: Value.ListValue = assertIs(second.value)
        assertEquals("two", assertIs<Value.Scalar>(nestedList.items[0]).value)
    }

    @Test
    fun encode_rejects_instanceValue() {
        val ex: IllegalArgumentException = assertFailsWith {
            codec.encode(Value.Instance(Any()))
        }

        assertTrue(ex.message!!.contains("runtime-only"))
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

// todo get rid of suffix numbers on names put in separate model package that is shared
@JvmInline
value class CustomerId1(val value: String)

class Person
class Address3