package au.clef.app.demo.model

@Suppress("unused")
class AcmeService {

    fun personName(person: Person): String {
        return person.name
    }

    fun personDescription(person: Person): String {
        return person.toString()
    }

    fun personAddress(person: Person): Address {
        return person.address
    }
}