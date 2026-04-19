package au.clef.app.demo

import au.clef.api.model.add
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Person
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.*
import au.clef.metadata.*
import au.clef.metadata.model.MetadataRoot
import java.io.File
import kotlin.reflect.jvm.javaMethod

private const val RESOURCE_PATH: String = "/config/method-metadata.json"
private val OUTPUT_FILE: File = File("src/main/resources").resolve(RESOURCE_PATH.removePrefix("/"))
private val DEMO_CLASSES: List<Class<*>> = listOf(AcmeService::class.java, Math::class.java)

fun main() {
    generateMetadata()
    validate()
    val engine: ReflectionEngine = createEngine()
    showAllDescriptors(engine)
    runGuiStyleInstance(engine)
    runGuiStyleStatic(engine)
    runKotlinTopLevel(engine)
}

private fun createEngine(): ReflectionEngine {
    val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(RESOURCE_PATH)
    return ReflectionEngine(metadataRegistry = DescriptorMetadataRegistry(metadata))
}

fun generateMetadata() {
    val generator = MetadataGenerator()
    val metadata: MetadataRoot = generator.generate(
        classes = DEMO_CLASSES, inheritanceLevel = InheritanceLevel.DeclaredOnly
    )
    MetadataWriter.writeToFile(metadata, OUTPUT_FILE)
    println("Metadata written to: ${OUTPUT_FILE.absolutePath}")
    println(MetadataWriter.toJson(metadata))
}

fun validate() {
    val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(RESOURCE_PATH)
    val validator = MetadataValidator()
    val issues: List<ValidationIssue> = validator.validate(metadata)
    if (issues.isEmpty()) {
        println("Metadata valid")
        return
    }
    issues.forEach { issue: ValidationIssue ->
        println("[${issue.severity}] ${issue.location} - ${issue.message}")
    }
}

fun showAllDescriptors(engine: ReflectionEngine) {
    val descriptors: List<MethodDescriptor> = engine.descriptors(AcmeService::class.java)
    descriptors.forEach { descriptor: MethodDescriptor ->
        println("METHOD: ${descriptor.id}")
        descriptor.parameters.forEach { param: ParamDescriptor ->
            println("  name=${param.name}, label=${param.label}, type=${param.type}")
        }
    }
}

fun runGuiStyleInstance(engine: ReflectionEngine) {
    val instance = AcmeService()
    val methodId: MethodId = MethodId.from(AcmeService::class, "personName", Person::class)
    val result: Any? = engine.invoke(methodId, instance, personValue())
    println("-----------> runGuiStyleInstance: $result")
}

fun runGuiStyleStatic(engine: ReflectionEngine) {
    val methodId: MethodId = MethodId.from(Math::class, "max", Int::class, Int::class)
    val result: Any? = engine.invoke(methodId, primitive("10"), primitive("20"))
    println("-----------> runGuiStyleStatic: $result")
}

fun runKotlinTopLevel(engine: ReflectionEngine) {
    val methodId: MethodId = MethodId.from(::add.javaMethod!!)
    val result: Any? = engine.invoke(methodId, Value.Primitive("10"), Value.Primitive("20"))
    println("-----------> runTopLevelFunction: $result")
}

private fun primitive(value: String): Value.Primitive = Value.Primitive(value)

private fun personValue(name: String = "Alice", age: String = "25"): Value.Object = Value.Object(
    type = Person::class.java, fields = mapOf("name" to primitive(name), "age" to primitive(age))
)