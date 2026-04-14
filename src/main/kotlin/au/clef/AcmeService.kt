package au.clef

import au.clef.model.Person

class AcmeService {

    fun personName(person: Person): String {
        return person.name
    }

    fun personDescription(person: Person): String { return person.toString()}
}