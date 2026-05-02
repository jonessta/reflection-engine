package au.clef.engine.model

import java.lang.reflect.Method
import kotlin.reflect.jvm.javaMethod
import kotlin.test.*

class MethodDescriptorTest {

    @Test
    fun from_javaMethod_buildsDescriptorFromJavaReflection() {
        val javaMethod: Method =
            SampleJavaMethods::class.java.getDeclaredMethod(
                "join",
                String::class.java,
                Int::class.javaPrimitiveType!!
            )

        val descriptor: MethodDescriptor = MethodDescriptor.from(javaMethod)

        assertEquals(MethodId.from(javaMethod), descriptor.id)
        assertEquals("join", descriptor.reflectedName)
        assertEquals(null, descriptor.displayName)
        assertEquals(String::class.java, descriptor.returnType)
        assertFalse(descriptor.isStatic)

        assertEquals(2, descriptor.parameters.size)

        val first: ParamDescriptor = descriptor.parameters[0]
        assertEquals(0, first.index)
        assertEquals(String::class.java, first.logicalType)
        assertEquals(String::class.java, first.runtimeType)
        assertEquals(first.reflectedName, first.name)
        assertTrue(first.nullable)

        val second: ParamDescriptor = descriptor.parameters[1]
        assertEquals(1, second.index)
        assertEquals<Class<out Any>?>(Int::class.javaPrimitiveType, second.logicalType)
        assertEquals<Class<out Any>?>(Int::class.javaPrimitiveType, second.runtimeType)
        assertEquals(second.reflectedName, second.name)
        assertFalse(second.nullable)
    }

    @Test
    fun from_javaMethod_withLogicalMethodName_overridesReflectedName() {
        val javaMethod: Method =
            SampleJavaMethods::class.java.getDeclaredMethod(
                "join",
                String::class.java,
                Int::class.javaPrimitiveType!!
            )

        val methodId: MethodId = MethodId.from(javaMethod)

        val descriptor: MethodDescriptor =
            MethodDescriptor.from(
                javaMethod = javaMethod,
                id = methodId,
                logicalMethodName = "logicalJoin",
                displayName = "Logical Join"
            )

        assertEquals(methodId, descriptor.id)
        assertEquals("logicalJoin", descriptor.reflectedName)
        assertEquals("Logical Join", descriptor.displayName)
        assertEquals(String::class.java, descriptor.returnType)
        assertFalse(descriptor.isStatic)
    }

    @Test
    fun from_kotlinFunction_usesKotlinFunctionName() {
        val javaMethod: Method =
            SampleKotlinMethods::nullableEcho.javaMethod
                ?: fail("Expected javaMethod for nullableEcho")

        val methodId: MethodId = MethodId.from(javaMethod)

        val descriptor: MethodDescriptor =
            MethodDescriptor.from(
                kotlinFunction = SampleKotlinMethods::nullableEcho,
                javaMethod = javaMethod,
                id = methodId,
                displayName = "Nullable Echo"
            )

        assertEquals(methodId, descriptor.id)
        assertEquals("nullableEcho", descriptor.reflectedName)
        assertEquals("Nullable Echo", descriptor.displayName)
        assertEquals(String::class.java, descriptor.returnType)
        assertFalse(descriptor.isStatic)

        assertEquals(2, descriptor.parameters.size)

        val first: ParamDescriptor = descriptor.parameters[0]
        assertEquals(0, first.index)
        assertEquals(String::class.java, first.logicalType)
        assertEquals(String::class.java, first.runtimeType)
        assertEquals("value", first.reflectedName)
        assertEquals("value", first.name)
        assertTrue(first.nullable)

        val second: ParamDescriptor = descriptor.parameters[1]
        assertEquals(1, second.index)
        assertEquals(Int::class.javaObjectType, second.logicalType)
        assertEquals(Int::class.javaObjectType, second.runtimeType)
        assertEquals("count", second.reflectedName)
        assertEquals("count", second.name)
        assertTrue(second.nullable)
    }

    @Test
    fun from_javaMethod_detectsStaticMethod() {
        val javaMethod: Method =
            SampleStatics1::class.java.getDeclaredMethod(
                "sum",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            )

        val descriptor: MethodDescriptor = MethodDescriptor.from(javaMethod)

        assertTrue(descriptor.isStatic)
        assertEquals<Class<out Any>?>(Int::class.javaPrimitiveType, descriptor.returnType)
    }

    @Test
    fun withMetadata_returnsNewDescriptorWithUpdatedMetadata() {
        val javaMethod: Method =
            SampleJavaMethods::class.java.getDeclaredMethod(
                "join",
                String::class.java,
                Int::class.javaPrimitiveType!!
            )

        val original: MethodDescriptor = MethodDescriptor.from(javaMethod)

        val updatedParameters: List<ParamDescriptor> =
            listOf(
                ParamDescriptor(
                    index = 0,
                    logicalType = String::class.java,
                    runtimeType = String::class.java,
                    reflectedName = "arg0",
                    name = "text",
                    nullable = true
                ),
                original.parameters[1]
            )

        val updated: MethodDescriptor =
            original.withMetadata(
                displayName = "Joined Text",
                parameters = updatedParameters
            )

        assertEquals(original.id, updated.id)
        assertEquals(original.reflectedName, updated.reflectedName)
        assertEquals("Joined Text", updated.displayName)
        assertEquals("text", updated.parameters[0].name)

        assertEquals(null, original.displayName)
        assertEquals(original.parameters[0].reflectedName, original.parameters[0].name)
    }

    @Test
    fun equality_and_hashCode_are_basedOnIdOnly() {
        val javaMethod: Method =
            SampleJavaMethods::class.java.getDeclaredMethod(
                "join",
                String::class.java,
                Int::class.javaPrimitiveType!!
            )

        val first: MethodDescriptor =
            MethodDescriptor.from(
                javaMethod = javaMethod,
                id = MethodId.from(javaMethod),
                logicalMethodName = "joinA",
                displayName = "First"
            )

        val second: MethodDescriptor =
            MethodDescriptor.from(
                javaMethod = javaMethod,
                id = MethodId.from(javaMethod),
                logicalMethodName = "joinB",
                displayName = "Second"
            )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun equality_distinguishesDifferentMethods() {
        val firstMethod: Method =
            SampleJavaMethods::class.java.getDeclaredMethod(
                "join",
                String::class.java,
                Int::class.javaPrimitiveType!!
            )

        val secondMethod: Method =
            SampleJavaMethods::class.java.getDeclaredMethod(
                "echo",
                String::class.java
            )

        val first: MethodDescriptor = MethodDescriptor.from(firstMethod)
        val second: MethodDescriptor = MethodDescriptor.from(secondMethod)

        assertNotEquals(first, second)
    }

    @Test
    fun toString_containsImportantFields() {
        val javaMethod: Method =
            SampleJavaMethods::class.java.getDeclaredMethod(
                "echo",
                String::class.java
            )

        val descriptor: MethodDescriptor =
            MethodDescriptor.from(
                javaMethod = javaMethod,
                id = MethodId.from(javaMethod),
                logicalMethodName = "logicalEcho",
                displayName = "Echo"
            )

        val text: String = descriptor.toString()

        assertTrue(text.contains("MethodDescriptor("))
        assertTrue(text.contains("reflectedName=logicalEcho"))
        assertTrue(text.contains("displayName=Echo"))
        assertTrue(text.contains(descriptor.id.toString()))
    }
}

class SampleJavaMethods {

    fun join(text: String, count: Int): String =
        text + count

    fun echo(text: String): String =
        text
}

object SampleKotlinMethods {

    fun nullableEcho(value: String?, count: Int?): String =
        "${value}:${count}"
}

class SampleStatics1 {

    companion object {
        @JvmStatic
        fun sum(a: Int, b: Int): Int =
            a + b
    }
}