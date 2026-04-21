package au.clef.app.demo.model

data class Address(val number: Int, val street: String, val zipCode: String)

data class Person(val name: String, val age: Int, val address: Address)