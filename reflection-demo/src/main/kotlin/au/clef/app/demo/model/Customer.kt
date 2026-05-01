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

    fun findCustomer(id: CustomerId): Customer =
        Customer(
            id = id,
            name = "Alice",
            email = EmailAddress("alice@example.com"),
            address = Address(
                number = 2,
                street = "Smith St",
                zipCode = "2321"
            )
        )

    fun normalizeEmail(email: EmailAddress): EmailAddress =
        EmailAddress(email.value.trim().lowercase())
}