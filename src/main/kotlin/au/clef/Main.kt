package au.clef

import au.clef.model.Person

fun main(args: Array<String>) {
    val app = App()
    runPrimitive(app)
//    runComposite(app)
}

fun runPrimitive(app: App) {
    val rawMethods = app.collectMethods(
        Math::class.java,
        InheritanceLevel.All
    )

    val methods = app.buildMethods(rawMethods)

    val maxMethod = methods.first {
        it.name == "max" &&
                it.rawMethod.parameterTypes.contentEquals(
                    arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                )
    }

    val inputArgs = listOf(
        Value.Primitive("10"),
        Value.Primitive("20")
    )

    println(maxMethod)

    val result = maxMethod.invoke(
        ExecutionContext(),
        inputArgs
    )

    println(result) // 20
}

fun runComposite(app: App) {
    val personValue: Value.Object = Value.Object(
        type = Person::class.java,
        fields = mapOf(
            "name" to Value.Primitive("Alice"),
            "age" to Value.Primitive("25")
        )
    )
    val person = app.materialize(personValue, Person::class.java)
    println(person)

}