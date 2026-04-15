package au.clef

import au.clef.model.AcmeService
import au.clef.model.Person

fun main() {
    val metadata = MetadataLoader.fromResourceOrEmpty("/config/method-metadata.json")
    val engine = ReflectionEngine(metadataRegistry = DescriptorMetadataRegistry(metadata))

    showAllDescriptors(engine)
    runGuiStyleInstance(engine)
    runGuiStyleStatic(engine)
    runKotlinTopLevel(engine)
}

// --------------------------GUI calls --------------------------------------

fun showAllDescriptors(reflectionEngine: ReflectionEngine) {
    val descriptors = reflectionEngine.descriptors(clazz = Math::class.java, InheritanceLevel.DeclaredOnly)
    for (descriptor in descriptors) {
        println(descriptor.name + " = " + descriptor.parameters.joinToString(", ") { it.name })
    }
}

fun runGuiStyleInstance(reflectionEngine: ReflectionEngine) {
    val service = AcmeService()
    val descriptor: MethodDescriptor = reflectionEngine.findDescriptorExact(
        clazz = AcmeService::class.java, methodName = "personName", parameterTypes = listOf(Person::class.java)
    )
    val result: Any? = reflectionEngine.invokeDescriptor(
        descriptor = descriptor, instance = service, args = listOf(personValue())
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
        descriptor = descriptor, args = listOf(Value.Primitive("10"), Value.Primitive("20"))
    )
    println("-----------> runGuiStyleStatic: $result")
}

private fun personValue(name: String = "Alice", age: String = "25"): Value.Object = Value.Object(
    type = Person::class.java, fields = mapOf("name" to Value.Primitive(name), "age" to Value.Primitive(age))
)

fun add(a: Int, b: Int): Int = a + b

fun runKotlinTopLevel(reflectionEngine: ReflectionEngine) {
    val descriptor: MethodDescriptor = reflectionEngine.descriptor(::add)
    val result: Any? = reflectionEngine.invokeDescriptor(
        descriptor = descriptor, args = listOf(
            Value.Primitive("10"), Value.Primitive("20")
        )
    )
    println("-----------> runTopLevelFunction: $result")
}