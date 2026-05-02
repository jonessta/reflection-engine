package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.ScalarValue
import au.clef.api.model.Value
import au.clef.engine.ObjectConstructionException
import java.lang.reflect.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypeConverterTest {

    private val converter: TypeConverter = TypeConverter(
        scalarRegistry = ScalarTypeRegistry()
    )

    @Test
    fun materialize_convertsScalarToIntPrimitive() {
        val result: Any? =
            converter.materialize(
                Value.Scalar(ScalarValue.NumberValue("42")),
                Int::class.javaPrimitiveType!!
            )

        assertEquals(42, result)
    }

    @Test
    fun materialize_convertsScalarToBoxedInt() {
        val result: Any? =
            converter.materialize(
                Value.Scalar(ScalarValue.NumberValue("42")),
                Int::class.javaObjectType
            )

        assertEquals(42, result)
    }

    @Test
    fun materialize_convertsScalarToBoolean() {
        val result: Any? =
            converter.materialize(
                Value.Scalar(ScalarValue.BooleanValue(true)),
                Boolean::class.javaObjectType
            )

        assertEquals(true, result)
    }

    @Test
    fun materialize_convertsListWithWildcardElementType() {
        val listType: Type =
            object : TypeReference<List<String>>() {}.type

        val result: Any? =
            converter.materialize(
                Value.ListValue(
                    items = listOf(
                        Value.Scalar(ScalarValue.StringValue("a")),
                        Value.Scalar(ScalarValue.StringValue("b"))
                    )
                ),
                listType
            )

        val list: MutableList<*> = assertIs(result)
        assertEquals<List<Any?>>(listOf("a", "b"), list)
    }

    @Test
    fun materialize_convertsScalarToEnumIgnoringCase() {
        val result: Any? =
            converter.materialize(
                Value.Scalar(ScalarValue.StringValue("active")),
                SampleStatus::class.java
            )

        assertEquals(SampleStatus.ACTIVE, result)
    }

    @Test
    fun materialize_rejectsInvalidEnumValue() {
        assertFailsWith<IllegalArgumentException> {
            converter.materialize(
                Value.Scalar(ScalarValue.StringValue("missing")),
                SampleStatus::class.java
            )
        }
    }

    @Test
    fun materialize_returnsNullForReferenceType() {
        val result: Any? =
            converter.materialize(
                Value.Null,
                String::class.java
            )

        assertNull(result)
    }

    @Test
    fun materialize_rejectsNullForPrimitiveType() {
        assertFailsWith<TypeMismatchException> {
            converter.materialize(
                Value.Null,
                Int::class.javaPrimitiveType!!
            )
        }
    }

    @Test
    fun supportsScalarTarget_returnsTrueForScalarLikeType() {
        assertTrue(converter.supportsScalarTarget(String::class.java))
        assertTrue(converter.supportsScalarTarget(Int::class.javaObjectType))
    }

    @Test
    fun supportsScalarTarget_returnsFalseForStructuredType() {
        assertFalse(converter.supportsScalarTarget(SamplePerson::class.java))
    }

    @Test
    fun materialize_convertsListToMutableList() {
        val result: Any? =
            converter.materialize(
                Value.ListValue(
                    items = listOf(
                        Value.Scalar(ScalarValue.StringValue("a")),
                        Value.Scalar(ScalarValue.StringValue("b"))
                    )
                ),
                object : TypeReference<List<String>>() {}.type
            )

        val list: MutableList<*> = assertIs(result)
        assertEquals<List<Any?>>(listOf("a", "b"), list)
    }

    @Test
    fun materialize_convertsListToSet() {
        val result: Any? =
            converter.materialize(
                Value.ListValue(
                    items = listOf(
                        Value.Scalar(ScalarValue.StringValue("a")),
                        Value.Scalar(ScalarValue.StringValue("a")),
                        Value.Scalar(ScalarValue.StringValue("b"))
                    )
                ),
                object : TypeReference<Set<String>>() {}.type
            )

        val set: Set<*> = assertIs(result)
        assertEquals(setOf("a", "b"), set)
    }

    @Test
    fun materialize_convertsListToArray() {
        val result: Any? =
            converter.materialize(
                Value.ListValue(
                    items = listOf(
                        Value.Scalar(ScalarValue.NumberValue("1")),
                        Value.Scalar(ScalarValue.NumberValue("2")),
                        Value.Scalar(ScalarValue.NumberValue("3"))
                    )
                ),
                Array<Int>::class.java
            )

        val array: Array<*> = assertIs(result)
        assertEquals(listOf(1, 2, 3), array.toList())
    }

    @Test
    fun materialize_rejectsListForNonCollectionTarget() {
        assertFailsWith<TypeMismatchException> {
            converter.materialize(
                Value.ListValue(
                    items = listOf(
                        Value.Scalar(ScalarValue.StringValue("a"))
                    )
                ),
                String::class.java
            )
        }
    }

    @Test
    fun materialize_convertsMapUsingParameterizedTypes() {
        val result: Any? =
            converter.materialize(
                Value.MapValue(
                    entries = listOf(
                        MapEntry(
                            key = Value.Scalar(ScalarValue.StringValue("a")),
                            value = Value.Scalar(ScalarValue.NumberValue("1"))
                        ),
                        MapEntry(
                            key = Value.Scalar(ScalarValue.StringValue("b")),
                            value = Value.Scalar(ScalarValue.NumberValue("2"))
                        )
                    )
                ),
                object : TypeReference<Map<String, Int>>() {}.type
            )

        val map: MutableMap<*, *> = assertIs(result)
        assertEquals(1, map["a"])
        assertEquals(2, map["b"])
    }

    @Test
    fun materialize_rejectsMapForNonMapTarget() {
        assertFailsWith<TypeMismatchException> {
            converter.materialize(
                Value.MapValue(
                    entries = listOf(
                        MapEntry(
                            key = Value.Scalar(ScalarValue.StringValue("a")),
                            value = Value.Scalar(ScalarValue.NumberValue("1"))
                        )
                    )
                ),
                String::class.java
            )
        }
    }

    @Test
    fun materialize_buildsKotlinObjectUsingPrimaryConstructor() {
        val result: Any? =
            converter.materialize(
                Value.Record(
                    type = SamplePerson::class.java,
                    fields = mapOf(
                        "name" to Value.Scalar(ScalarValue.StringValue("Alice")),
                        "age" to Value.Scalar(ScalarValue.NumberValue("25"))
                    )
                ),
                SamplePerson::class.java
            )

        val person: SamplePerson = assertIs(result)
        assertEquals("Alice", person.name)
        assertEquals(25, person.age)
    }

    @Test
    fun materialize_buildsKotlinObjectUsingDefaultParameter() {
        val result: Any? =
            converter.materialize(
                Value.Record(
                    type = SampleWithDefault::class.java,
                    fields = mapOf(
                        "name" to Value.Scalar(ScalarValue.StringValue("Alice"))
                    )
                ),
                SampleWithDefault::class.java
            )

        val value: SampleWithDefault = assertIs(result)
        assertEquals("Alice", value.name)
        assertEquals(99, value.age)
    }

    @Test
    fun materialize_rejectsMissingMandatoryKotlinParameter() {
        val ex: ObjectConstructionException =
            assertFailsWith {
                converter.materialize(
                    Value.Record(
                        type = SamplePerson::class.java,
                        fields = mapOf(
                            "name" to Value.Scalar(ScalarValue.StringValue("Alice"))
                        )
                    ),
                    SamplePerson::class.java
                )
            }

        assertTrue(ex.message!!.contains("Missing mandatory parameter 'age'"))
    }

    @Test
    fun materialize_buildsJavaObjectUsingSingleConstructor() {
        val result: Any? =
            converter.materialize(
                Value.Record(
                    type = JavaOnlyCtor::class.java,
                    fields = mapOf(
                        "arg0" to Value.Scalar(ScalarValue.StringValue("Bob")),
                        "arg1" to Value.Scalar(ScalarValue.NumberValue("41"))
                    )
                ),
                JavaOnlyCtor::class.java
            )

        val value: JavaOnlyCtor = assertIs(result)
        assertEquals("Bob", value.name)
        assertEquals(41, value.age)
    }

    @Test
    fun materialize_rejectsMissingJavaConstructorArgument() {
        val ex: ObjectConstructionException =
            assertFailsWith {
                converter.materialize(
                    Value.Record(
                        type = JavaOnlyCtor::class.java,
                        fields = mapOf(
                            "arg0" to Value.Scalar(ScalarValue.StringValue("Bob"))
                        )
                    ),
                    JavaOnlyCtor::class.java
                )
            }

        assertTrue(ex.message!!.contains("Missing constructor argument"))
    }

    @Test
    fun materialize_buildsObjectUsingNoArgConstructorAndFields() {
        val result: Any? =
            converter.materialize(
                Value.Record(
                    type = MutableBean::class.java,
                    fields = mapOf(
                        "name" to Value.Scalar(ScalarValue.StringValue("Chris")),
                        "age" to Value.Scalar(ScalarValue.NumberValue("50"))
                    )
                ),
                MutableBean::class.java
            )

        val bean: MutableBean = assertIs(result)
        assertEquals("Chris", bean.name)
        assertEquals(50, bean.age)
    }

    @Test
    fun materialize_rejectsUnknownFieldForNoArgConstruction() {
        val ex: ObjectConstructionException =
            assertFailsWith {
                converter.materialize(
                    Value.Record(
                        type = MutableBean::class.java,
                        fields = mapOf(
                            "missing" to Value.Scalar(ScalarValue.StringValue("x"))
                        )
                    ),
                    MutableBean::class.java
                )
            }

        assertTrue(ex.message!!.contains("Field 'missing' not found"))
    }
}

enum class SampleStatus {
    ACTIVE,
    INACTIVE
}

data class SamplePerson(
    val name: String,
    val age: Int
)

data class SampleWithDefault(
    val name: String,
    val age: Int = 99
)

class JavaOnlyCtor(
    val name: String,
    val age: Int
)

class MutableBean {
    var name: String = ""
    var age: Int = 0
}

abstract class TypeReference<T> {
    val type: Type
        get() = (javaClass.genericSuperclass as java.lang.reflect.ParameterizedType)
            .actualTypeArguments[0]
}