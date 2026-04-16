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

    generateMetadata()
//    validate()

//    showAllDescriptors(engine)
//    runGuiStyleInstance(engine)
//    runGuiStyleStatic(engine)
//    runKotlinTopLevel(engine)
}

fun generateMetadata() {
    val generator = MetadataGenerator()
    val metadata: MetadataRoot = generator.generate(
        classes = listOf(AcmeService::class.java), inheritanceLevel = InheritanceLevel.DeclaredOnly
    )

    MetadataWriter.writeToFile(metadata, outputFile)

    println("Metadata written to: ${outputFile.absolutePath}")
    println(MetadataWriter.toJson(metadata))
}

fun validate() {
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
    val all: List<MethodDescriptor> = reflectionEngine.descriptors(AcmeService::class.java)

    all.forEach { descriptor: MethodDescriptor ->
        println("METHOD: ${descriptor.id}")
        descriptor.parameters.forEach { param: ParamDescriptor ->
            println("  name=${param.name}, label=${param.label}, type=${param.type}")
        }
    }
}

fun runGuiStyleInstance(reflectionEngine: ReflectionEngine) {
    val service = AcmeService()
    val descriptor: MethodDescriptor = reflectionEngine.findDescriptorExact(
        clazz = AcmeService::class.java, methodName = "personName", parameterTypes = listOf(Person::class.java)
    )

    val binding: MethodBinding = reflectionEngine.findBindingById(
        clazz = AcmeService::class.java, id = descriptor.id
    )

    val result: Any? = reflectionEngine.invokeBinding(
        binding = binding, instance = service, args = listOf(personValue())
    )

    println("-----------> runGuiStyleInstance: $result")
}

fun runGuiStyleStatic(reflectionEngine: ReflectionEngine) {
    val descriptor: MethodDescriptor = reflectionEngine.findDescriptorExact(
        clazz = Math::class.java, methodName = "max", parameterTypes = listOf(
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
        )
    )

    val binding: MethodBinding = reflectionEngine.findBindingById(
        clazz = Math::class.java, id = descriptor.id
    )

    val result: Any? = reflectionEngine.invokeBinding(
        binding = binding, args = listOf(
            Value.Primitive("10"), Value.Primitive("20")
        )
    )

    println("-----------> runGuiStyleStatic: $result")
}

fun add(a: Int, b: Int): Int = a + b

fun runKotlinTopLevel(reflectionEngine: ReflectionEngine) {
    val declaringClass: Class<*> = Class.forName("au.clef.MainKt")

    val descriptor: MethodDescriptor = reflectionEngine.findDescriptorExact(
        clazz = declaringClass, methodName = "add", parameterTypes = listOf(
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
        )
    )

    val binding: MethodBinding = reflectionEngine.findBindingById(
        clazz = declaringClass, id = descriptor.id
    )

    val result: Any? = reflectionEngine.invokeBinding(
        binding = binding, args = listOf(
            Value.Primitive("10"), Value.Primitive("20")
        )
    )

    println("-----------> runTopLevelFunction: $result")
}

private fun personValue(
    name: String = "Alice", age: String = "25"
): Value.Object = Value.Object(
    type = Person::class.java, fields = mapOf(
        "name" to Value.Primitive(name), "age" to Value.Primitive(age)
    )
)