package au.clef.app.demo

import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Person
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.*
import au.clef.metadata.*
import au.clef.metadata.model.MetadataRoot
import java.io.File

const val resourcePath = "/config/method-metadata.json"
val outputFile = File("src/main/resources").resolve(resourcePath.removePrefix("/"))

fun main() {
    val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(resourcePath)
    val engine = ReflectionEngine(metadataRegistry = DescriptorMetadataRegistry(metadata))

    generateMetadata()
    validate()
    showAllDescriptors(engine)
    runGuiStyleInstance(engine)
    runGuiStyleStatic(engine)
    runKotlinTopLevel(engine)
}

fun generateMetadata() {
    val generator = MetadataGenerator()
    val metadata: MetadataRoot = generator.generate(
        classes = listOf(AcmeService::class.java, Math::class.java), inheritanceLevel = InheritanceLevel.DeclaredOnly
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

fun showAllDescriptors(engine: ReflectionEngine) {
    val all: List<MethodDescriptor> = engine.descriptors(AcmeService::class.java)
    all.forEach { descriptor: MethodDescriptor ->
        println("METHOD: ${descriptor.id}")
        descriptor.parameters.forEach { param: ParamDescriptor ->
            println("  name=${param.name}, label=${param.label}, type=${param.type}")
        }
    }
}

fun runGuiStyleInstance(engine: ReflectionEngine) {
    val instance = AcmeService()
    val methodId = MethodId.from(AcmeService::class, "personName", Person::class)
    val result = engine.invoke(methodId, instance, personValue())
    println("-----------> $result")
}

fun runGuiStyleStatic(engine: ReflectionEngine) {
    val methodId = MethodId.from(Math::class, "max", Int::class, Int::class)
    val result: Any? = engine.invoke(methodId, Value.Primitive("10"), Value.Primitive("20"))
    println("-----------> runGuiStyleStatic: $result")
}

fun add(a: Int, b: Int): Int = a + b

fun runKotlinTopLevel(engine: ReflectionEngine) {
    val declaringClass: Class<*> = Class.forName("au.clef.app.demo.MainKt")
    val methodId = MethodId.from(
        declaringClass, "add", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
    )
    val result: Any? = engine.invoke(methodId, Value.Primitive("10"), Value.Primitive("20"))
    println("-----------> runTopLevelFunction: $result")
}

private fun personValue(name: String = "Alice", age: String = "25"): Value.Object = Value.Object(
    type = Person::class.java, fields = mapOf(
        "name" to Value.Primitive(name), "age" to Value.Primitive(age)
    )
)