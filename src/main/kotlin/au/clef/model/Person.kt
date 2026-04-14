package au.clef.model

data class Person(val name: String, val age: Int) {
    override fun toString(): String {
        return "Person(name='$name', age=$age)"
    }

}