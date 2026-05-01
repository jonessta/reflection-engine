package au.clef.engine

import au.clef.engine.model.MethodId
import au.clef.metadata.DescriptorMetadataRegistry
import au.clef.metadata.model.MetadataRoot
import au.clef.metadata.model.MethodMetadata
import au.clef.metadata.model.ParamMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ReflectionEngineTest {

    @Test
    fun descriptors_returnsDescriptors_forRegisteredClass() {
        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.StaticClass(SampleStatics2::class)
            ).build()
        )

        val descriptors = engine.descriptors(SampleStatics2::class)

        assertTrue(
            descriptors.any { descriptor ->
                descriptor.reflectedName == "sum"
            }
        )
    }

    @Test
    fun descriptors_kclassAndClass_overloadsReturnSameDescriptors() {
        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.StaticClass(SampleStatics2::class)
            ).build()
        )

        val byKClass = engine.descriptors(SampleStatics2::class)
        val byClass = engine.descriptors(SampleStatics2::class.java)

        assertEquals(byClass.map { it.id }, byKClass.map { it.id })
    }

    @Test
    fun descriptor_returnsDecoratedDescriptor_whenMetadataExists() {
        val methodId: MethodId = MethodId.from(
            SampleService2::class,
            "greet",
            String::class
        )

        val metadata: MetadataRoot = MetadataRoot(
            methods = mapOf(
                methodId to MethodMetadata(
                    displayName = "Friendly Greeting",
                    parameters = listOf(
                        ParamMetadata(name = "personName")
                    )
                )
            )
        )

        val engine: ReflectionEngine = ReflectionEngine(
            reflectionConfig = reflectionConfig(
                MethodSource.Instance(SampleService2(), "Sample Service")
            ).build(),
            metadataRegistry = DescriptorMetadataRegistry(metadata)
        )

        val descriptor = engine.descriptor(methodId)

        assertEquals("Friendly Greeting", descriptor.displayName)
        assertEquals("personName", descriptor.parameters[0].name)
    }

    @Test
    fun invokeStatic_byMethodId_invokesStaticMethod() {
        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.StaticClass(SampleStatics2::class)
            ).build()
        )

        val methodId: MethodId = MethodId.from(
            SampleStatics2::class,
            "sum",
            Int::class,
            Int::class
        )

        val result: Any? = engine.invokeStatic(methodId, 2, 3)

        assertEquals(5, result)
    }

    @Test
    fun invokeStatic_byDescriptor_invokesStaticMethod() {
        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.StaticClass(SampleStatics2::class)
            ).build()
        )

        val methodId: MethodId = MethodId.from(
            SampleStatics2::class,
            "sum",
            Int::class,
            Int::class
        )

        val descriptor = engine.descriptor(methodId)

        val result: Any? = engine.invokeStatic(descriptor, listOf(4, 6))

        assertEquals(10, result)
    }

    @Test
    fun invokeInstance_byMethodId_invokesInstanceMethod() {
        val instance: SampleService2 = SampleService2()

        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.Instance(instance, "Sample Service")
            ).build()
        )

        val methodId: MethodId = MethodId.from(
            SampleService2::class,
            "greet",
            String::class
        )

        val result: Any? = engine.invokeInstance(methodId, instance, "Alice")

        assertEquals("Hello Alice", result)
    }

    @Test
    fun invokeInstance_byDescriptor_invokesInstanceMethod() {
        val instance: SampleService2 = SampleService2()

        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.Instance(instance, "Sample Service")
            ).build()
        )

        val methodId: MethodId = MethodId.from(
            SampleService2::class,
            "greet",
            String::class
        )

        val descriptor = engine.descriptor(methodId)

        val result: Any? = engine.invokeInstance(descriptor, instance, listOf("Bob"))

        assertEquals("Hello Bob", result)
    }

    @Test
    fun invokeStatic_throwsWhenArgCountIsWrong() {
        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.StaticClass(SampleStatics2::class)
            ).build()
        )

        val methodId: MethodId = MethodId.from(
            SampleStatics2::class,
            "sum",
            Int::class,
            Int::class
        )

        val ex: IllegalArgumentException =
            try {
                engine.invokeStatic(methodId, 1)
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                e
            }

        assertTrue(ex.message!!.contains("Expected 2 args"))
    }

    @Test
    fun invokeInstance_throwsWhenArgCountIsWrong() {
        val instance: SampleService2 = SampleService2()

        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.Instance(instance, "Sample Service")
            ).build()
        )

        val methodId: MethodId = MethodId.from(
            SampleService2::class,
            "greet",
            String::class
        )

        val ex: IllegalArgumentException =
            try {
                engine.invokeInstance(methodId, instance, emptyList())
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                e
            }

        assertTrue(ex.message!!.contains("Expected 1 args"))
    }

    @Test
    fun invokeInstance_throwsWhenDescriptorIsInstanceMethod_andInstanceIsNull() {
        val instance: SampleService2 = SampleService2()

        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.Instance(instance, "Sample Service")
            ).build()
        )

        val methodId: MethodId = MethodId.from(
            SampleService2::class,
            "greet",
            String::class
        )

        val descriptor = engine.descriptor(methodId)

        val ex: MissingInstanceException =
            try {
                engine.invokeStatic(descriptor, listOf("Alice"))
                fail("Expected MissingInstanceException")
            } catch (e: MissingInstanceException) {
                e
            }

        assertTrue(ex.message!!.contains(methodId.toString()))
    }

    @Test
    fun invokeInstance_throwsWhenDescriptorIsStatic_andInstanceProvided() {
        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.StaticClass(SampleStatics2::class)
            ).build()
        )

        val methodId: MethodId = MethodId.from(
            SampleStatics2::class,
            "sum",
            Int::class,
            Int::class
        )

        val descriptor = engine.descriptor(methodId)

        val ex: IllegalArgumentException =
            try {
                engine.invokeInstance(descriptor, Any(), listOf(1, 2))
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                e
            }

        assertTrue(ex.message!!.contains("must not receive an instance"))
    }

    @Test
    fun executionContexts_returnsRegisteredContexts() {
        val instance: SampleService2 = SampleService2()

        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.StaticClass(SampleStatics2::class),
                MethodSource.Instance(instance, "Sample Service")
            ).build()
        )

        val contexts: Collection<ExecutionContext> = engine.executionContexts()

        assertTrue(contexts.any { context -> context is ExecutionContext.Static })
        assertTrue(contexts.any { context -> context is ExecutionContext.Instance })
    }

    @Test
    fun executionContext_returnsMatchingContextByExecutionId() {
        val instance: SampleService2 = SampleService2()

        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.Instance(instance, "Sample Service")
            ).build()
        )

        val context: ExecutionContext.Instance =
            engine.executionContexts()
                .filterIsInstance<ExecutionContext.Instance>()
                .first()

        val resolved: ExecutionContext = engine.executionContext(context.executionId)

        val resolvedInstance: ExecutionContext.Instance = assertIs(resolved)
        assertEquals(context.executionId, resolvedInstance.executionId)
        assertEquals("Sample Service", resolvedInstance.instanceDescription)
        assertEquals(instance, resolvedInstance.instance)
    }

    @Test
    fun knownClasses_andDeclaringClasses_areExposedThroughEngine() {
        val engine: ReflectionEngine = testEngine(
            reflectionConfig(
                MethodSource.StaticClass(SampleStatics2::class)
            )
                .supportingTypes(SampleSupport::class)
                .build()
        )

        assertTrue(engine.declaringClasses.contains(SampleStatics2::class.java))
        assertTrue(engine.knownClasses.contains(SampleStatics2::class.java))
        assertTrue(engine.knownClasses.contains(SampleSupport::class.java))
    }

    @Test
    fun descriptors_returnsDecoratedList_whenMetadataExists() {
        val methodId: MethodId = MethodId.from(
            SampleService2::class,
            "greet",
            String::class
        )

        val metadata: MetadataRoot = MetadataRoot(
            methods = mapOf(
                methodId to MethodMetadata(
                    displayName = "Greeting",
                    parameters = listOf(
                        ParamMetadata(name = "who")
                    )
                )
            )
        )

        val engine: ReflectionEngine = ReflectionEngine(
            reflectionConfig = reflectionConfig(
                MethodSource.Instance(SampleService2(), "Sample Service")
            ).build(),
            metadataRegistry = DescriptorMetadataRegistry(metadata)
        )

        val descriptors = engine.descriptors(SampleService2::class)

        val greetingDescriptor =
            descriptors.firstOrNull { descriptor ->
                descriptor.id == methodId
            }

        assertNotNull(greetingDescriptor)
        assertEquals("Greeting", greetingDescriptor.displayName)
        assertEquals("who", greetingDescriptor.parameters[0].name)
    }

    private fun testEngine(config: ReflectionConfig): ReflectionEngine =
        ReflectionEngine(config, metadataRegistry = null)
}

class SampleService {

    fun greet(name: String): String =
        "Hello $name"
}

class SampleSupport

class SampleStatics {

    companion object {
        @JvmStatic
        fun sum(a: Int, b: Int): Int =
            a + b
    }
}