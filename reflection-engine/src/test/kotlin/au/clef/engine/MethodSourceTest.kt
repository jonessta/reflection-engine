package au.clef.engine

import au.clef.engine.model.MethodId
import kotlin.reflect.jvm.javaMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class MethodSourceTest {

    @Test
    fun staticClass_setsDeclaringClass() {
        val source: MethodSource.StaticClass =
            MethodSource.StaticClass(SampleStatics2::class)

        assertEquals(SampleStatics2::class, source.declaringClass)
    }

    @Test
    fun staticMethod_fromClassAndName_buildsMethodIdAndDeclaringClass() {
        val source: MethodSource.StaticMethod =
            MethodSource.StaticMethod(
                declaringClass = SampleStatics2::class,
                methodName = "sum",
                Int::class,
                Int::class
            )

        assertEquals(SampleStatics2::class, source.declaringClass)
        assertEquals(
            MethodId.from(SampleStatics2::class, "sum", Int::class, Int::class),
            source.methodId
        )
    }

    @Test
    fun staticMethod_fromKFunction_buildsMethodIdAndDeclaringClass() {
        val source: MethodSource.StaticMethod =
            MethodSource.StaticMethod(::topLevelAdd)

        val javaMethod = requireNotNull(::topLevelAdd.javaMethod)

        assertEquals(javaMethod.declaringClass.kotlin, source.declaringClass)
        assertEquals(MethodId.from(javaMethod), source.methodId)
    }

    @Test
    fun instance_setsDeclaringClassInstanceAndDescription() {
        val instance: SampleService2 = SampleService2()

        val source: MethodSource.Instance =
            MethodSource.Instance(
                instance = instance,
                instanceDescription = "Sample Service"
            )

        assertEquals(SampleService2::class, source.declaringClass)
        assertSame(instance, source.instance)
        assertEquals("Sample Service", source.instanceDescription)
        assertIs<MethodSource.ExposableInstance>(source)
    }

    @Test
    fun instanceMethod_fromMethodName_buildsMethodIdAndStoresInstanceData() {
        val instance: SampleService2 = SampleService2()

        val source: MethodSource.InstanceMethod =
            MethodSource.InstanceMethod(
                instance = instance,
                instanceDescription = "Sample Service",
                methodName = "greet",
                String::class
            )

        assertEquals(SampleService2::class, source.declaringClass)
        assertSame(instance, source.instance)
        assertEquals("Sample Service", source.instanceDescription)
        assertEquals(
            MethodId.from(SampleService2::class, "greet", String::class),
            source.methodId
        )
        assertIs<MethodSource.ExposableInstance>(source)
    }

    @Test
    fun instanceMethod_primaryConstructor_keepsProvidedMethodId() {
        val instance: SampleService2 = SampleService2()
        val methodId: MethodId = MethodId.from(SampleService2::class, "greet", String::class)

        val source: MethodSource.InstanceMethod =
            MethodSource.InstanceMethod(
                instance = instance,
                instanceDescription = "Sample Service",
                methodId = methodId
            )

        assertEquals(SampleService2::class, source.declaringClass)
        assertSame(instance, source.instance)
        assertEquals("Sample Service", source.instanceDescription)
        assertEquals(methodId, source.methodId)
    }
}

class SampleService2 {
    fun greet(name: String): String = "Hello $name"
}

class SampleStatics2 {
    companion object {
        @JvmStatic
        fun sum(a: Int, b: Int): Int = a + b
    }
}

fun topLevelAdd(a: Int, b: Int): Int = a + b