package au.clef.engine.convert

import au.clef.engine.ObjectConstructionException
import au.clef.engine.TypeMismatchException
import au.clef.engine.model.Value
import kotlin.test.*

class TypeConverterTest {

    private val converter: TypeConverter = TypeConverter()

    @Test
    fun materialize_convertsStringToIntPrimitive() {
        val result: Any? = converter.materialize(
            Value.Primitive("25"),
            Int::class.javaPrimitiveType!!
        )
        assertEquals(25, result)
    }

    @Test
    fun materialize_convertsStringToBoxedInt() {
        val result: Any? = converter.materialize(
            Value.Primitive("25"),
            Int::class.javaObjectType
        )
        assertEquals(25, result)
    }

    @Test
    fun materialize_convertsStringToLongPrimitive() {
        val result: Any? = converter.materialize(
            Value.Primitive("25"),
            Long::class.javaPrimitiveType!!
        )
        assertEquals(25L, result)
    }

    @Test
    fun materialize_convertsStringToDoublePrimitive() {
        val result: Any? = converter.materialize(
            Value.Primitive("25.5"),
            Double::class.javaPrimitiveType!!
        )
        assertEquals(25.5, result)
    }

    @Test
    fun materialize_convertsStringToBooleanPrimitive() {
        val result: Any? = converter.materialize(
            Value.Primitive("true"),
            Boolean::class.javaPrimitiveType!!
        )
        assertEquals(true, result)
    }

    @Test
    fun materialize_convertsStringToCharPrimitive() {
        val result: Any? = converter.materialize(
            Value.Primitive("A"),
            Char::class.javaPrimitiveType!!
        )
        assertEquals('A', result)
    }

    @Test
    fun materialize_convertsPrimitiveToString() {
        val result: Any? = converter.materialize(
            Value.Primitive(123),
            String::class.java
        )
        assertEquals("123", result)
    }

    @Test
    fun materialize_returnsPrimitiveValueDirectly_whenAlreadyCorrectType() {
        val result: Any? = converter.materialize(
            Value.Primitive(123),
            Int::class.javaObjectType
        )
        assertEquals(123, result)
    }

    @Test
    fun materialize_convertsEnumByName() {
        val result: Any? = converter.materialize(
            Value.Primitive("ACTIVE"),
            SampleStatus::class.java
        )
        assertEquals(SampleStatus.ACTIVE, result)
    }

    @Test
    fun materialize_rejectsInvalidEnumValue() {
        assertFailsWith<TypeMismatchException> {
            converter.materialize(
                Value.Primitive("MISSING"),
                SampleStatus::class.java
            )
        }
    }

    @Test
    fun materialize_returnsNullForReferenceType() {
        val result: Any? = converter.materialize(
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
    fun materialize_buildsKotlinDataClassFromNamedFields() {
        val result: Any? = converter.materialize(
            Value.Object(
                type = SamplePerson::class.java,
                fields = mapOf(
                    "name" to Value.Primitive("Alice"),
                    "age" to Value.Primitive("25")
                )
            ),
            SamplePerson::class.java
        )
        val person: SamplePerson = assertIs<SamplePerson>(result)
        assertEquals("Alice", person.name)
        assertEquals(25, person.age)
    }

    @Test
    fun materialize_rejectsMissingKotlinConstructorField() {
        val ex: ObjectConstructionException = assertFailsWith<ObjectConstructionException> {
            converter.materialize(
                Value.Object(
                    type = SamplePerson::class.java,
                    fields = mapOf("name" to Value.Primitive("Alice"))
                ),
                SamplePerson::class.java
            )
        }
        assertTrue(ex.message!!.contains("Missing constructor argument 'age'"))
    }

    @Test
    fun materialize_buildsJavaBeanStyleObjectWithNoArgConstructor() {
        val result: Any? = converter.materialize(
            Value.Object(
                type = SampleMutablePerson::class.java,
                fields = mapOf(
                    "name" to Value.Primitive("Bob"),
                    "age" to Value.Primitive("41")
                )
            ),
            SampleMutablePerson::class.java
        )
        val person: SampleMutablePerson = assertIs<SampleMutablePerson>(result)
        assertEquals("Bob", person.name)
        assertEquals(41, person.age)
    }

    @Test
    fun materialize_rejectsUnknownFieldForNoArgObjectConstruction() {
        val ex: ObjectConstructionException = assertFailsWith<ObjectConstructionException> {
            converter.materialize(
                Value.Object(
                    type = SampleMutablePerson::class.java,
                    fields = mapOf("missing" to Value.Primitive("x"))
                ),
                SampleMutablePerson::class.java
            )
        }
        assertTrue(ex.message!!.contains("No field 'missing' found"))
    }

    @Test
    fun materialize_rejectsObjectWhenDeclaredTypeDoesNotMatchTargetType() {
        assertFailsWith<TypeMismatchException> {
            converter.materialize(
                Value.Object(type = SampleMutablePerson::class.java, fields = emptyMap()),
                SamplePerson::class.java
            )
        }
    }

    @Test
    fun materialize_returnsInstanceWhenCompatible() {
        val source = SampleMutablePerson()
        source.name = "Chris"
        source.age = 50
        val result: Any? =
            converter.materialize(Value.Instance(source), SampleMutablePerson::class.java)

        val instance: SampleMutablePerson = assertIs<SampleMutablePerson>(result)
        assertEquals("Chris", instance.name)
        assertEquals(50, instance.age)
    }

    @Test
    fun materialize_rejectsInstanceWhenIncompatible() {
        assertFailsWith<TypeMismatchException> {
            converter.materialize(
                Value.Instance("wrong"),
                SampleMutablePerson::class.java
            )
        }
    }

    @Test
    fun materialize_rejectsInvalidBooleanText() {
        assertFailsWith<IllegalArgumentException> {
            converter.materialize(
                Value.Primitive("yes"),
                Boolean::class.javaPrimitiveType!!
            )
        }
    }

    @Test
    fun materialize_rejectsInvalidCharText() {
        assertFailsWith<IllegalArgumentException> {
            converter.materialize(
                Value.Primitive("AB"),
                Char::class.javaPrimitiveType!!
            )
        }
    }

    @Test
    fun materialize_marksReferenceFieldAsNullableInConstructedObjectFlow() {
        val result: Any? = converter.materialize(
            Value.Object(
                type = SampleOptionalHolder::class.java,
                fields = mapOf("name" to Value.Null)
            ),
            SampleOptionalHolder::class.java
        )
        val holder: SampleOptionalHolder = assertIs<SampleOptionalHolder>(result)
        assertNull(holder.name)
    }
}

enum class SampleStatus {
    ACTIVE,
    INACTIVE
}

data class SamplePerson(val name: String, val age: Int)

class SampleMutablePerson {
    var name: String = "";
    var age: Int = 0;
}

data class SampleOptionalHolder(val name: String?)