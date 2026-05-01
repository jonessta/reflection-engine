package au.clef.engine.registry

import au.clef.engine.ExecutionContext
import au.clef.engine.MethodSource
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import kotlin.reflect.jvm.javaMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MethodSourceRegistryTest {

    @Test
    fun instanceSource_usesKotlinLogicalNames_forValueClassMethods() {
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
    fun instanceMethodSource_usesKotlinLogicalName_forSingleMethod() {
        val service: CustomerService = CustomerService()

        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.InstanceMethod(
                    instance = service,
                    instanceDescription = "Customer Service",
                    methodName = "findCustomer",
                    CustomerId::class
                )
            )
        )

        val methodId: MethodId =
            MethodId.from(CustomerService::class, "findCustomer", CustomerId::class)

        val descriptor: MethodDescriptor = registry.descriptor(methodId)

        assertEquals("findCustomer", descriptor.reflectedName)
        assertFalse(descriptor.reflectedName.contains("-"))
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

        val methodNames: List<String> =
            instanceContexts.map { context: ExecutionContext.Instance ->
                registry.descriptor(context.methodId).reflectedName
            }

        assertTrue(methodNames.contains("findCustomer"))
        assertTrue(methodNames.contains("normalizeEmail"))

        instanceContexts.forEach { context: ExecutionContext.Instance ->
            assertEquals(service, context.instance)
            assertEquals("Customer Service", context.instanceDescription)
        }
    }

    @Test
    fun instanceSource_registersExpectedDeclaringClass_andKnownClasses() {
        val service: CustomerService = CustomerService()

        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(service, "Customer Service")
            ),
            methodSupportingTypes = listOf(
                Customer::class,
                Address::class
            )
        )

        assertTrue(registry.declaringClasses.contains(CustomerService::class.java))
        assertTrue(registry.knownClasses.contains(CustomerService::class.java))
        assertTrue(registry.knownClasses.contains(Customer::class.java))
        assertTrue(registry.knownClasses.contains(Address::class.java))
    }

    @Test
    fun instanceSource_doesNotExposeAnyMembers() {
        val service: CustomerService = CustomerService()

        val registry: MethodSourceRegistry = MethodSourceRegistry(
            methodSources = listOf(
                MethodSource.Instance(service, "Customer Service")
            )
        )

        val descriptors: List<MethodDescriptor> = registry.descriptors(CustomerService::class.java)

        assertTrue(
            descriptors.none { descriptor: MethodDescriptor ->
                descriptor.reflectedName == "equals" ||
                        descriptor.reflectedName == "hashCode" ||
                        descriptor.reflectedName == "toString"
            }
        )
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

fun topLevelAdd(a: Int, b: Int): Int = a + b