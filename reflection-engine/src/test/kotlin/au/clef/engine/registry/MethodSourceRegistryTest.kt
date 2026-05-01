package au.clef.engine.registry

import au.clef.engine.ExecutionContext
import au.clef.engine.MethodNotFoundException
import au.clef.engine.MethodSource
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import kotlin.reflect.jvm.javaMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MethodSourceRegistryTest {

    @Test
    fun instanceSource_usesLogicalKotlinNames_forValueClassMethods() {
        val service: CustomerService = CustomerService()

        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(service, "Customer Service")
            )
        )

        val descriptors: List<MethodDescriptor> =
            registry.descriptors(CustomerService::class.java)

        val names: List<String> =
            descriptors.map { descriptor: MethodDescriptor -> descriptor.reflectedName }

        assertTrue(names.contains("findCustomer"))
        assertTrue(names.contains("normalizeEmail"))

        assertTrue(names.none { name: String -> name.startsWith("findCustomer-") })
        assertTrue(names.none { name: String -> name.startsWith("normalizeEmail-") })
    }

    @Test
    fun instanceMethodSource_usesLogicalKotlinName_forSingleMethod() {
        val service: CustomerService = CustomerService()

        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.InstanceMethod(
                    instance = service,
                    instanceDescription = "Customer Service",
                    function = CustomerService::findCustomer
                )
            )
        )

        val descriptors: List<MethodDescriptor> =
            registry.descriptors(CustomerService::class.java)

        assertEquals(1, descriptors.size)
        assertEquals("findCustomer", descriptors.single().reflectedName)
        assertFalse(descriptors.single().reflectedName.contains("-"))
    }

    @Test
    fun staticMethodSource_forTopLevelKotlinFunction_usesLogicalName() {
        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.StaticMethod(::topLevelAdd)
            )
        )

        val methodId: MethodId = MethodId.from(::topLevelAdd.javaMethod!!)
        val descriptor: MethodDescriptor = registry.descriptor(methodId)

        assertEquals("topLevelAdd", descriptor.reflectedName)
        assertFalse(descriptor.reflectedName.contains("-"))
        assertTrue(descriptor.isStatic)
    }

    @Test
    fun staticClass_registersOnlyStaticMethods() {
        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.StaticClass(MixedMethods::class)
            )
        )

        val descriptors: List<MethodDescriptor> =
            registry.descriptors(MixedMethods::class.java)

        assertTrue(descriptors.any { descriptor: MethodDescriptor -> descriptor.reflectedName == "staticEcho" })
        assertTrue(descriptors.none { descriptor: MethodDescriptor -> descriptor.reflectedName == "instanceEcho" })
    }

    @Test
    fun instanceSource_registersOnlyInstanceMethods() {
        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(MixedMethods(), "Mixed")
            )
        )

        val descriptors: List<MethodDescriptor> =
            registry.descriptors(MixedMethods::class.java)

        assertTrue(descriptors.any { descriptor: MethodDescriptor -> descriptor.reflectedName == "instanceEcho" })
        assertTrue(descriptors.none { descriptor: MethodDescriptor -> descriptor.reflectedName == "staticEcho" })
    }

    @Test
    fun instanceSource_createsInstanceExecutionContexts() {
        val service: CustomerService = CustomerService()

        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(service, "Customer Service")
            )
        )

        val instanceContexts: List<ExecutionContext.Instance> =
            registry.allExecutionContexts().filterIsInstance<ExecutionContext.Instance>()

        assertTrue(instanceContexts.isNotEmpty())

        val names: List<String> =
            instanceContexts.map { context: ExecutionContext.Instance ->
                registry.descriptor(context.methodId).reflectedName
            }

        assertTrue(names.contains("findCustomer"))
        assertTrue(names.contains("normalizeEmail"))

        instanceContexts.forEach { context: ExecutionContext.Instance ->
            assertEquals(service, context.instance)
            assertEquals("Customer Service", context.instanceDescription)
        }
    }

    @Test
    fun staticSource_createsStaticExecutionContexts() {
        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.StaticClass(MixedMethods::class)
            )
        )

        val staticContexts: List<ExecutionContext.Static> =
            registry.allExecutionContexts().filterIsInstance<ExecutionContext.Static>()

        assertTrue(staticContexts.isNotEmpty())

        val names: List<String> =
            staticContexts.map { context: ExecutionContext.Static ->
                registry.descriptor(context.methodId).reflectedName
            }

        assertTrue(names.contains("staticEcho"))
    }

    @Test
    fun executionContext_returnsMatchingContextByExecutionId() {
        val service: CustomerService = CustomerService()

        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(service, "Customer Service")
            )
        )

        val context: ExecutionContext.Instance =
            registry.allExecutionContexts()
                .filterIsInstance<ExecutionContext.Instance>()
                .first()

        val resolved: ExecutionContext = registry.executionContext(context.executionId)

        val resolvedInstance: ExecutionContext.Instance = assertIs(resolved)
        assertEquals(context.executionId, resolvedInstance.executionId)
        assertEquals(service, resolvedInstance.instance)
        assertEquals("Customer Service", resolvedInstance.instanceDescription)
    }

    @Test
    fun declaringClasses_and_knownClasses_includeSupportingTypes() {
        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(CustomerService(), "Customer Service")
            ),
            methodSupportingTypes = listOf(Customer::class, Address::class)
        )

        assertTrue(registry.declaringClasses.contains(CustomerService::class.java))
        assertTrue(registry.knownClasses.contains(CustomerService::class.java))
        assertTrue(registry.knownClasses.contains(Customer::class.java))
        assertTrue(registry.knownClasses.contains(Address::class.java))
    }

    @Test
    fun descriptors_throwsForUnregisteredClass() {
        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(CustomerService(), "Customer Service")
            )
        )

        val ex: IllegalArgumentException =
            try {
                registry.descriptors(String::class.java)
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                e
            }

        assertTrue(ex.message!!.contains("Not registered"))
    }

    @Test
    fun descriptor_throwsForUnknownMethodId() {
        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(CustomerService(), "Customer Service")
            )
        )

        val unknownMethodId: MethodId =
            MethodId.fromValue("com.example.Missing#nope()")

        val ex: MethodNotFoundException =
            try {
                registry.descriptor(unknownMethodId)
                fail("Expected MethodNotFoundException")
            } catch (e: MethodNotFoundException) {
                e
            }

        assertEquals(unknownMethodId, ex.methodId)
    }

    @Test
    fun staticMethod_registration_rejectsInstanceMethodId() {
        val instanceMethodId: MethodId =
            MethodId.from(MixedMethods::class, "instanceEcho", String::class)

        val ex: IllegalArgumentException =
            try {
                MethodSourceRegistry(
                    methodSources = listOf(
                        MethodSource.StaticMethod(
                            declaringClass = MixedMethods::class,
                            methodName = "instanceEcho",
                            String::class
                        )
                    )
                )
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                e
            }

        assertTrue(ex.message!!.contains("must be static"))
    }

    @Test
    fun instanceMethod_registration_rejectsStaticMethodId() {
        val ex: IllegalArgumentException =
            try {
                MethodSourceRegistry(
                    methodSources = listOf(
                        MethodSource.InstanceMethod(
                            instance = MixedMethods(),
                            instanceDescription = "Mixed",
                            methodName = "staticEcho",
                            String::class
                        )
                    )
                )
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                e
            }

        assertTrue(ex.message!!.contains("must be an instance method"))
    }

    @Test
    fun inheritanceLevel_declaredOnly_excludes_parentMethods() {
        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(ChildService(), "Child")
            ),
            inheritanceLevel = InheritanceLevel.DeclaredOnly
        )

        val descriptors: List<MethodDescriptor> =
            registry.descriptors(ChildService::class.java)

        val names: List<String> =
            descriptors.map { descriptor: MethodDescriptor -> descriptor.reflectedName }

        assertTrue(names.contains("childOnly"))
        assertFalse(names.contains("parentOnly"))
    }

    @Test
    fun inheritanceLevel_all_includes_parentMethods() {
        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(ChildService(), "Child")
            ),
            inheritanceLevel = InheritanceLevel.All
        )

        val descriptors: List<MethodDescriptor> =
            registry.descriptors(ChildService::class.java)

        val names: List<String> =
            descriptors.map { descriptor: MethodDescriptor -> descriptor.reflectedName }

        assertTrue(names.contains("childOnly"))
        assertTrue(names.contains("parentOnly"))
    }
}

@JvmInline
value class CustomerId(
    val value: String
)

@JvmInline
value class EmailAddress(
    val value: String
)

data class Address(
    val line1: String
)

data class Customer(
    val id: CustomerId,
    val email: EmailAddress,
    val address: Address?
)

class CustomerService {

    fun findCustomer(customerId: CustomerId): Customer =
        Customer(
            id = customerId,
            email = EmailAddress("alice@example.com"),
            address = Address("1 Main St")
        )

    fun normalizeEmail(emailAddress: EmailAddress): EmailAddress =
        EmailAddress(emailAddress.value.lowercase())
}

open class ParentService {
    fun parentOnly(): String = "parent"
}

class ChildService : ParentService() {
    fun childOnly(): String = "child"
}

class MixedMethods {

    fun instanceEcho(value: String): String = value

    companion object {
        @JvmStatic
        fun staticEcho(value: String): String = value
    }
}

fun topLevelAdd(a: Int, b: Int): Int = a + b