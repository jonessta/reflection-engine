package au.clef

import au.clef.model.OverloadedService
import au.clef.model.Person

fun main() {
    val app = App()
    runComposite(app)
    runAcme(app)
    runStatic(app)
    runStaticAmbiguous(app)

    runOverloadedAmbiguous(app)
    runOverloadedExact(app)

    runGuiStyleInstance(app)
    runGuiStyleStatic(app)
}

fun runComposite(app: App) {
    val personInput = personValue()
    val person = app.materialize(personInput, Person::class.java)
    println("-----------> runComposite: $person")
}

fun runAcme(app: App) {
    val personInput = personValue()
    val result = app.call(
        target = AcmeService(),
        methodName = "personName",
        args = listOf(personInput)
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
        paramTypes = listOf(
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
    )
    println("-----------> runStatic: $result")
}

fun runStaticAmbiguous(app: App) {
    try {
        val result = app.callStatic(
            clazz = Math::class.java,
            methodName = "max",
            args = listOf(
                Value.Primitive("10"),
                Value.Primitive("20")
            )
        )
        println("-----------> runStaticAmbiguous UNEXPECTED SUCCESS: $result")
    } catch (e: IllegalArgumentException) {
        println("-----------> runStaticAmbiguous: ${e.message}")
    }
}

fun runOverloadedAmbiguous(app: App) {
    try {
        val result = app.call(
            target = OverloadedService(),
            methodName = "format",
            args = listOf(Value.Primitive("10"))
        )
        println("-----------> runOverloadedAmbiguous UNEXPECTED: $result")
    } catch (e: IllegalArgumentException) {
        println("-----------> runOverloadedAmbiguous: ${e.message}")
    }
}

fun runOverloadedExact(app: App) {
    val result = app.callExact(
        target = OverloadedService(),
        methodName = "format",
        args = listOf(Value.Primitive("10")),
        paramTypes = listOf(Int::class.javaPrimitiveType!!)
    )
    println("-----------> runOverloadedExact (Int): $result")

    val result2 = app.callExact(
        target = OverloadedService(),
        methodName = "format",
        args = listOf(Value.Primitive("10")),
        paramTypes = listOf(String::class.java)
    )
    println("-----------> runOverloadedExact (String): $result2")
}

// --------------------------GUI calls --------------------------------------
fun runGuiStyleInstance(app: App) {
    val service = AcmeService()
    val descriptor = app.buildMethods(
        app.collectMethods(AcmeService::class.java, InheritanceLevel.DeclaredOnly)
    ).first { it.name == "personName" }

    val result = app.invokeDescriptor(
        descriptor = descriptor,
        instance = service,
        args = listOf(
            personValue()
        )
    )
    println("-----------> runGuiStyleInstance: $result")
}

fun runGuiStyleStatic(app: App) {
    val descriptor = app.buildMethods(
        app.collectMethods(Math::class.java, InheritanceLevel.DeclaredOnly)
    ).first {
        it.name == "max" &&
                it.isStatic &&
                it.rawMethod.parameterTypes.contentEquals(
                    arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                )
    }
    val result = app.invokeDescriptor(
        descriptor = descriptor,
        args = listOf(
            Value.Primitive("10"),
            Value.Primitive("20")
        )
    )
    println("-----------> runGuiStyleStatic: $result")
}

private fun personValue(name: String = "Alice", age: String = "25") = Value.Object(
    type = Person::class.java,
    fields = mapOf("name" to Value.Primitive(name), "age" to Value.Primitive(age))
)