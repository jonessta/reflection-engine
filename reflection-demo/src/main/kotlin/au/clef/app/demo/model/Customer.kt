package au.clef.app.demo.model

@JvmInline
value class CustomerId(val value: String)

@JvmInline
value class EmailAddress(val value: String)

data class Customer(
    val id: CustomerId,
    val name: String,
    val email: EmailAddress,
    val address: Address
)

@Suppress("unused")
class CustomerService {

    private val customers = mutableMapOf<String, Customer>()

    fun findCustomer(id: CustomerId): Customer =
        Customer(
            id = id,
            name = "Alice",
            email = EmailAddress("alice@example.com"),
            address = Address(
                number = 2,
                street = "Smith St",
                zipCode = ZipCode("2321")
            )
        )

    fun normalizeEmail(email: EmailAddress): EmailAddress =
        EmailAddress(email.value.trim().lowercase())

    fun addCustomer(customer: Customer) {
        customers[customer.id.value] = customer
    }
}