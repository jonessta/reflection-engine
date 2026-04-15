package au.clef

import au.clef.model.Person

fun main() {
    val reflectionEngine = ReflectionEngine()
//    runComposite(app)
//    runAcme(app)
//    runStatic(app)
//    runStaticAmbiguous(app)
//
//    runOverloadedAmbiguous(app)
//    runOverloadedExact(app)

    runGuiStyleInstance(reflectionEngine)
    runGuiStyleStatic(reflectionEngine)
    runKotlinTopLevel(reflectionEngine)
}

//fun runComposite(app: App) {
//    val personInput: Value.Object = personValue()
//    val person = app.materialize(personInput, Person::class.java)
//    println("-----------> runComposite: $person")
//}

//fun runAcme(app: App) {
//    val personInput: Value.Object = personValue()
//    val result = app.call(
//        target = AcmeService(),
//        methodName = "personName",
//        args = listOf(personInput)
//    )
//    println("-----------> runAcme: $result")
//}

//fun runStatic(app: App) {
//    val result = app.callStaticExact(
//        clazz = Math::class.java,
//        methodName = "max",
//        args = listOf(
//            Value.Primitive("10"),
//            Value.Primitive("20")
//        ),
//        parameterTypes = listOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
//    )
//    println("-----------> runStatic: $result")
//}
//
//fun runStaticAmbiguous(app: App) {
//    try {
//        val result = app.callStatic(
//            clazz = Math::class.java,
//            methodName = "max",
//            args = listOf(Value.Primitive("10"), Value.Primitive("20"))
//        )
//        println("-----------> runStaticAmbiguous UNEXPECTED SUCCESS: $result")
//    } catch (e: AmbiguousMethodException) {
//        println("-----------> runStaticAmbiguous: ${e.message}")
//    }
//}

//fun runOverloadedAmbiguous(app: App) {
//    try {
//        val result = app.call(
//            target = OverloadedService(),
//            methodName = "format",
//            args = listOf(Value.Primitive("10"))
//        )
//        println("-----------> runOverloadedAmbiguous UNEXPECTED: $result")
//    } catch (e: AmbiguousMethodException) {
//        println("-----------> runOverloadedAmbiguous: ${e.message}")
//    }
//}
//
//fun runOverloadedExact(app: App) {
//    val result = app.callExact(
//        target = OverloadedService(),
//        methodName = "format",
//        args = listOf(Value.Primitive("10")),
//        parameterTypes = listOf(Int::class.javaPrimitiveType!!)
//    )
//    println("-----------> runOverloadedExact (Int): $result")
//
//    val result2 = app.callExact(
//        target = OverloadedService(),
//        methodName = "format",
//        args = listOf(Value.Primitive("10")),
//        parameterTypes = listOf(String::class.java)
//    )
//    println("-----------> runOverloadedExact (String): $result2")
//}

// --------------------------GUI calls --------------------------------------
fun runGuiStyleInstance(reflectionEngine: ReflectionEngine) {
    val service = AcmeService()
    val descriptor: MethodDescriptor = reflectionEngine.findDescriptorExact(
        clazz = AcmeService::class.java,
        methodName = "personName",
        parameterTypes = listOf(Person::class.java)
    )
    val result: Any? = reflectionEngine.invokeDescriptor(
        descriptor = descriptor,
        instance = service,
        args = listOf(personValue())
    )
    println("-----------> runGuiStyleInstance: $result")
}

fun runGuiStyleStatic(reflectionEngine: ReflectionEngine) {
    val descriptor: MethodDescriptor = reflectionEngine.findDescriptorExact(
        clazz = Math::class.java,
        methodName = "max",
        parameterTypes = listOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    )
    val result = reflectionEngine.invokeDescriptor(
        descriptor = descriptor,
        args = listOf(Value.Primitive("10"), Value.Primitive("20"))
    )
    println("-----------> runGuiStyleStatic: $result")
}

private fun personValue(name: String = "Alice", age: String = "25"): Value.Object = Value.Object(
    type = Person::class.java,
    fields = mapOf("name" to Value.Primitive(name), "age" to Value.Primitive(age))
)

fun add(a: Int, b: Int): Int = a + b

fun runKotlinTopLevel(reflectionEngine: ReflectionEngine) {
    val descriptor: MethodDescriptor = reflectionEngine.descriptor(::add)
    val result: Any? = reflectionEngine.invokeDescriptor(
        descriptor = descriptor,
        args = listOf(
            Value.Primitive("10"),
            Value.Primitive("20")
        )
    )
    println("-----------> runTopLevelFunction: $result")
}