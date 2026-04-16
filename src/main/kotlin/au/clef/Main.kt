package au.clef

import au.clef.model.AcmeService
import au.clef.model.Person
import java.io.File

const val resourcePath = "/config/method-metadata.json"
val outputFile = File("src/main/resources").resolve(resourcePath.removePrefix("/"))

fun main() {
    val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(resourcePath)
    val engine = ReflectionEngine(
        metadataRegistry = DescriptorMetadataRegistry(metadata)
    )
//    generateMetadata()
    showAllDescriptors(engine)
//    runGuiStyleInstance(engine)
//    runGuiStyleStatic(engine)
//    runKotlinTopLevel(engine)
}

// --------------------------GUI calls --------------------------------------

fun generateMetadata() {

    val generator = MetadataGenerator()
    val metadata: MetadataRoot = generator.generate(
        classes = listOf(
            AcmeService::class.java,
            Math::class.java
        ),
        inheritanceLevel = InheritanceLevel.DeclaredOnly
    )
    MetadataWriter.writeToFile(metadata, outputFile)
    println("Metadata written to: ${outputFile.absolutePath}")
    println(MetadataWriter.toJson(metadata))
}

fun validate() {
    // use at startup
    val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(resourcePath)

    val validator = MetadataValidator()
    val issues: List<ValidationIssue> = validator.validate(metadata)

    if (issues.isEmpty()) {
        println("Metadata valid")
    } else {
        issues.forEach { issue ->
            println("[${issue.severity}] ${issue.location} - ${issue.message}")
        }
    }
}

fun showAllDescriptors(reflectionEngine: ReflectionEngine) {
//    val descriptors = reflectionEngine.descriptors(clazz = Math::class.java, InheritanceLevel.DeclaredOnly)
//    for (descriptor in descriptors) {
//        println(descriptor.name + " = " + descriptor.parameters.joinToString(", ") { it.name })
//    }

    val all: List<MethodDescriptor> = reflectionEngine.descriptors(AcmeService::class.java, InheritanceLevel.DeclaredOnly)
    all.forEach { d ->
        println("METHOD: ${d.name}")
        d.parameters.forEach { p ->
            println("  name=${p.name}, label=${p.label}")
        }
    }
}

fun runGuiStyleInstance(reflectionEngine: ReflectionEngine) {
    val service = AcmeService()
    val descriptor: MethodDescriptor = reflectionEngine.findDescriptorExact(
        clazz = AcmeService::class.java, methodName = "personName", parameterTypes = listOf(Person::class.java)
    )
    println(descriptor)
    descriptor.parameters.forEach {
        println("name=${it.name}, label=${it.label}")
    }
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