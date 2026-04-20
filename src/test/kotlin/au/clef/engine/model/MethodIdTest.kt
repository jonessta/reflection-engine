package au.clef.engine.model

import java.lang.reflect.Method
import kotlin.test.*

class MethodModelTest {

    @Test
    fun methodId_fromMethod_buildsExpectedValue_forInstanceMethod() {
        val method: Method =
            SampleService::class.java.getDeclaredMethod("personName", SamplePerson::class.java)
        val id: MethodId = MethodId.from(method)
        assertEquals(
            "au.clef.engine.model.SampleService#personName(au.clef.engine.model.SamplePerson)",
            id.value
        )
    }

    @Test
    fun methodId_fromMethod_buildsExpectedValue_forStaticMethod() {
        val method: Method = java.lang.Math::class.java.getDeclaredMethod(
            "max",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        val id: MethodId = MethodId.from(method)
        assertEquals(
            "java.lang.Math#max(int,int)",
            id.value
        )
    }

    @Test
    fun methodId_fromKClass_buildsExpectedValue() {
        val id: MethodId = MethodId.from(
            SampleService::class,
            "personName",
            SamplePerson::class
        )
        assertEquals(
            "au.clef.engine.model.SampleService#personName(au.clef.engine.model.SamplePerson)",
            id.value
        )
    }

    @Test
    fun methodId_fromValue_acceptsValidMethodId_withoutParameters() {
        val id: MethodId = MethodId.fromValue("au.clef.engine.model.SampleService#ping()")
        assertEquals(
            "au.clef.engine.model.SampleService#ping()",
            id.value
        )
    }

    @Test
    fun methodId_fromValue_acceptsValidMethodId_withParameters() {
        val id: MethodId = MethodId.fromValue(
            "au.clef.engine.model.SampleService#personName(au.clef.engine.model.SamplePerson)"
        )
        assertEquals(
            "au.clef.engine.model.SampleService#personName(au.clef.engine.model.SamplePerson)",
            id.value
        )
    }

    @Test
    fun methodId_fromValue_rejectsMissingClassSeparator() {
        val ex: IllegalMethodIdException =
            try {
                MethodId.fromValue("au.clef.engine.model.SampleService.personName()")
                fail("Expected IllegalMethodIdException")
            } catch (e: IllegalMethodIdException) {
                e
            }

        assertTrue(ex.message!!.contains("expected <class>#<method>(<paramTypes>)"))
    }

    @Test
    fun methodId_fromValue_rejectsEmptyParameterSlot() {
        val ex: IllegalMethodIdException =
            try {
                MethodId.fromValue("java.lang.Math#max(int,,int)")
                fail("Expected IllegalMethodIdException")
            } catch (e: IllegalMethodIdException) {
                e
            }

        assertTrue(ex.message!!.contains("comma-separated with no empty entries"))
    }

    @Test
    fun methodId_fromValue_rejectsMalformedParameterType() {
        val ex: IllegalMethodIdException =
            try {
                MethodId.fromValue("java.lang.Math#max(int, bad-type)")
                fail("Expected IllegalMethodIdException")
            } catch (e: IllegalMethodIdException) {
                e
            }

        assertTrue(ex.message!!.contains("parameter type names are malformed"))
    }

    @Test
    fun methodDescriptor_derivesFieldsFromMethod() {
        val method: Method = SampleService::class.java.getDeclaredMethod("personName", SamplePerson::class.java)
        val descriptor = MethodDescriptor(method)
        assertEquals("personName", descriptor.reflectedName)
        assertEquals(String::class.java, descriptor.returnType)
        assertFalse(descriptor.isStatic)
        assertEquals(MethodId.from(method), descriptor.id)
    }

    @Test
    fun methodDescriptor_detectsStaticMethod() {
        val method: Method = SampleStatics::class.java.getDeclaredMethod(
            "sum",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        val descriptor = MethodDescriptor(method)
        assertTrue(descriptor.isStatic)
        assertEquals<Class<out Any>?>(Int::class.javaPrimitiveType, descriptor.returnType)
    }

    @Test
    fun methodDescriptor_buildsParameterDescriptors() {
        val method: Method = SampleService::class.java.getDeclaredMethod("personName", SamplePerson::class.java)
        val descriptor = MethodDescriptor(method)
        assertEquals(1, descriptor.parameters.size)

        val param: ParamDescriptor = descriptor.parameters[0]
        assertEquals(0, param.index)
        assertEquals(SamplePerson::class.java, param.type)
        assertEquals(param.reflectedName, param.name)
        assertTrue(param.nullable)
    }

    @Test
    fun methodDescriptor_marksPrimitiveParameters_asNotNullable() {
        val method: Method = SampleStatics::class.java.getDeclaredMethod(
            "sum",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        val descriptor = MethodDescriptor(method)

        assertEquals(2, descriptor.parameters.size)
        assertFalse(descriptor.parameters[0].nullable)
        assertFalse(descriptor.parameters[1].nullable)
    }

    @Test
    fun methodDescriptor_equality_isBasedOnMethodId() {
        val method1: Method =
            SampleService::class.java.getDeclaredMethod("personName", SamplePerson::class.java)

        val method2: Method =
            SampleService::class.java.getDeclaredMethod("personName", SamplePerson::class.java)

        val descriptor1 = MethodDescriptor(method1, displayName = "First")
        val descriptor2 = MethodDescriptor(method2, displayName = "Second")

        assertEquals(descriptor1, descriptor2)
        assertEquals(descriptor1.hashCode(), descriptor2.hashCode())
    }

    @Test
    fun methodDescriptor_equality_distinguishesDifferentOverloads() {
        val method1: Method =
            SampleOverloads::class.java.getDeclaredMethod("echo", String::class.java)

        val method2: Method = SampleOverloads::class.java.getDeclaredMethod(
            "echo",
            Int::class.javaPrimitiveType!!
        )
        val descriptor1 = MethodDescriptor(method1)
        val descriptor2 = MethodDescriptor(method2)
        assertNotEquals(descriptor1, descriptor2)
    }

    @Test
    fun illegalMethodIdException_isEngineException() {
        val ex: Throwable =
            try {
                MethodId.fromValue("bad")
                fail("Expected IllegalMethodIdException")
            } catch (e: Throwable) {
                e
            }

        assertIs<IllegalMethodIdException>(ex)
        assertIs<au.clef.engine.EngineException>(ex)
    }
}

class SampleService {
    fun personName(person: SamplePerson): String = person.name
    fun ping(): String = "pong"
}

data class SamplePerson(
    val name: String,
    val age: Int
)

class SampleOverloads {
    fun echo(value: String): String = value
    fun echo(value: Int): Int = value
}

class SampleStatics {
    companion object {
        @JvmStatic
        fun sum(a: Int, b: Int): Int = a + b
    }
}