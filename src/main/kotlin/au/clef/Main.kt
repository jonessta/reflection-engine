package au.clef

import au.clef.model.Person

fun main() {
    val app = App()
    runComposite(app)
    runAcme(app)
    runStatic(app)
    runStaticAmbiguous(app)
}

fun runComposite(app: App) {
    val personValue = personValue()
    val person = app.materialize(personValue, Person::class.java)
    println("-----------> runComposite: $person")
}

fun runAcme(app: App) {
    val personValue = personValue()
    val result = app.call(
        target = AcmeService(),
        methodName = "personName",
        args = listOf(personValue)
    )
    println("-----------> runAcme: $result")
}

fun runStatic(app: App) {
    val result = app.callStaticExact(
        clazz = Math::class.java,
        methodName = "max",
        args = listOf(
            Value.Primitive("10"),
            Value.Primitive("20")
        ),
        parameterTypes = listOf(
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
    )
    println("-----------> runStatic: $result")
}

fun runStaticAmbiguous(app: App) {
    try {
        app.callStatic(
            clazz = Math::class.java,
            methodName = "max",
            args = listOf(
                Value.Primitive("10"),
                Value.Primitive("20")
            )
        )
    } catch (e: IllegalArgumentException) {
        println("-----------> runStaticAmbiguous: ${e.message}")
    }
}

private fun personValue(name: String = "Alice", age: String = "25") = Value.Object(
    type = Person::class.java,
    fields = mapOf(
        "name" to Value.Primitive(name),
        "age" to Value.Primitive(age)
    )
)