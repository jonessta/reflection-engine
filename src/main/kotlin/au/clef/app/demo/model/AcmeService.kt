package au.clef.app.demo.model

class AcmeService {

    fun personName(person: Person): String {
        return person.name
    }

    fun personDescription(person: Person): String {
        return person.toString()
    }
}