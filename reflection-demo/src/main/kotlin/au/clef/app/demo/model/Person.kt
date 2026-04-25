package au.clef.app.demo.model

import kotlinx.serialization.Serializable

@Serializable
data class Address(val number: Int, val street: String, val zipCode: String)

@Serializable
data class Person(val name: String, val age: Int, val address: Address)