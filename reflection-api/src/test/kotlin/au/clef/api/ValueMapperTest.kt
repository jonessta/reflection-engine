package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.MapEntryDto
import au.clef.api.model.Value
import au.clef.api.model.ValueDto
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValueMapperTest {

    @Test
    fun toEngineValue_mapsScalar() {
        val mapper: ValueMapper = ValueMapper(FakeClassResolver())

        val result: Value =
            mapper.toEngineValue(
                ValueDto.Scalar(JsonPrimitive("abc"))
            )

        val scalar: Value.Scalar = assertIs(result)
        assertEquals(JsonPrimitive("abc"), scalar.value)
    }

    @Test
    fun toEngineValue_mapsNull() {
        val mapper: ValueMapper = ValueMapper(FakeClassResolver())

        val result: Value = mapper.toEngineValue(ValueDto.Null)

        assertEquals(Value.Null, result)
    }

    @Test
    fun toEngineValue_mapsListRecursively() {
        val mapper: ValueMapper = ValueMapper(FakeClassResolver())

        val result: Value =
            mapper.toEngineValue(
                ValueDto.ListValue(
                    items = listOf(
                        ValueDto.Scalar(JsonPrimitive("a")),
                        ValueDto.Null,
                        ValueDto.Scalar(JsonPrimitive("b"))
                    )
                )
            )

        val listValue: Value.ListValue = assertIs(result)
        assertEquals(3, listValue.items.size)
        assertIs<Value.Scalar>(listValue.items[0])
        assertEquals(Value.Null, listValue.items[1])
        assertIs<Value.Scalar>(listValue.items[2])
    }

    @Test
    fun toEngineValue_mapsMapRecursively() {
        val mapper: ValueMapper = ValueMapper(FakeClassResolver())

        val result: Value =
            mapper.toEngineValue(
                ValueDto.MapValue(
                    entries = listOf(
                        MapEntryDto(
                            key = ValueDto.Scalar(JsonPrimitive("k1")),
                            value = ValueDto.Scalar(JsonPrimitive("v1"))
                        ),
                        MapEntryDto(
                            key = ValueDto.Scalar(JsonPrimitive("k2")),
                            value = ValueDto.ListValue(
                                items = listOf(
                                    ValueDto.Scalar(JsonPrimitive("x")),
                                    ValueDto.Null
                                )
                            )
                        )
                    )
                )
            )

        val mapValue: Value.MapValue = assertIs(result)
        assertEquals(2, mapValue.entries.size)

        val first: MapEntry = mapValue.entries[0]
        assertIs<Value.Scalar>(first.key)
        assertIs<Value.Scalar>(first.value)

        val second: MapEntry = mapValue.entries[1]
        assertIs<Value.Scalar>(second.key)
        assertIs<Value.ListValue>(second.value)
    }

    @Test
    fun toEngineValue_mapsRecordUsingStructuredType() {
        val resolver: FakeClassResolver = FakeClassResolver().apply {
            putStructured("Person", Person::class.java)
        }

        val mapper: ValueMapper = ValueMapper(resolver)

        val result: Value =
            mapper.toEngineValue(
                ValueDto.Record(
                    type = "Person",
                    fields = mapOf(
                        "name" to ValueDto.Scalar(JsonPrimitive("Alice")),
                        "age" to ValueDto.Scalar(JsonPrimitive("25"))
                    )
                )
            )

        val record: Value.Record = assertIs(result)
        assertEquals(Person::class.java, record.type)
        assertEquals(2, record.fields.size)
        assertIs<Value.Scalar>(record.fields.getValue("name"))
        assertIs<Value.Scalar>(record.fields.getValue("age"))
    }

    @Test
    fun toEngineValue_mapsNestedRecordFieldsRecursively() {
        val resolver: FakeClassResolver = FakeClassResolver().apply {
            putStructured("Person", Person::class.java)
            putStructured("Address", Address1::class.java)
        }

        val mapper: ValueMapper = ValueMapper(resolver)

        val result: Value =
            mapper.toEngineValue(
                ValueDto.Record(
                    type = "Person",
                    fields = mapOf(
                        "name" to ValueDto.Scalar(JsonPrimitive("Alice")),
                        "address" to ValueDto.Record(
                            type = "Address",
                            fields = mapOf(
                                "line1" to ValueDto.Scalar(JsonPrimitive("1 Main St"))
                            )
                        )
                    )
                )
            )

        val record: Value.Record = assertIs(result)
        val nested: Value.Record = assertIs(record.fields.getValue("address"))
        assertEquals(Address1::class.java, nested.type)
        assertIs<Value.Scalar>(nested.fields.getValue("line1"))
    }

    @Test
    fun toEngineValue_rejectsRecordWhenResolvedTypeIsScalar() {
        val resolver: FakeClassResolver = FakeClassResolver().apply {
            putScalar("CustomerId", String::class.java)
        }

        val mapper: ValueMapper = ValueMapper(resolver)

        val ex: IllegalArgumentException =
            assertFailsWith {
                mapper.toEngineValue(
                    ValueDto.Record(
                        type = "CustomerId",
                        fields = mapOf(
                            "value" to ValueDto.Scalar(JsonPrimitive("c1"))
                        )
                    )
                )
            }

        assertTrue(ex.message!!.contains("scalar-like"))
    }

    @Test
    fun toEngineValue_propagatesResolverFailure() {
        val mapper: ValueMapper = ValueMapper(FakeClassResolver())

        assertFailsWith<IllegalArgumentException> {
            mapper.toEngineValue(
                ValueDto.Record(
                    type = "MissingType",
                    fields = emptyMap()
                )
            )
        }
    }
}

private class FakeClassResolver : ClassResolver {

    private val resolvedTypes: MutableMap<String, ResolvedType> = mutableMapOf()

    fun putScalar(typeName: String, clazz: Class<*>) {
        resolvedTypes[typeName] = ResolvedType.Scalar(clazz)
    }

    fun putStructured(typeName: String, clazz: Class<*>) {
        resolvedTypes[typeName] = ResolvedType.Structured(clazz)
    }

    override fun resolve(typeName: String): ResolvedType =
        resolvedTypes[typeName]
            ?: throw IllegalArgumentException("Unknown type: $typeName")
}

private class Person
private class Address1