package au.clef.app.demo.model

data class Person(val name: String, val age: Int, val address: Address)

@JvmInline
value class ZipCode(val code: String) {
    init {
       require(code.isNotEmpty()) { "ZipCode must be non-empty" }
    }
}

data class Address(val number: Int, val street: String, val zipCode: ZipCode)

