package au.clef.app.demo

import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.*
import au.clef.engine.model.Values.scalar
import au.clef.engine.registry.MethodRegistry
import au.clef.metadata.*
import au.clef.metadata.model.MetadataRoot
import java.io.File
import kotlin.reflect.jvm.javaMethod

private const val RESOURCE_PATH: String = "/config/method-metadata.json"
private val OUTPUT_FILE: File = File("src/main/resources").resolve(RESOURCE_PATH.removePrefix("/"))

fun main() {
    generateMetadata()
    validate()
    val engine: ReflectionEngine = createEngine()
    showAllDescriptors(engine)
    runGuiStyleInstance(engine)
    runGuiStyleStatic(engine)
    runKotlinTopLevel(engine)
}

val methodRegistry = MethodRegistry(
    AcmeService::class,
    Math::class,
    Class.forName("au.clef.app.demo.model.KotlinFuncsKt").kotlin
)

private fun createEngine(): ReflectionEngine {
    val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(RESOURCE_PATH)
    return ReflectionEngine(
        methodRegistry = methodRegistry,
        metadataRegistry = DescriptorMetadataRegistry(metadata)
    )
}

fun generateMetadata() {
    val generator = MetadataGenerator(methodRegistry = methodRegistry)
    val metadata: MetadataRoot = generator.generate()
    MetadataWriter.writeToFile(metadata, OUTPUT_FILE)
    println("Metadata written to: ${OUTPUT_FILE.absolutePath}")
    println(MetadataWriter.toJson(metadata))
}

fun validate() {
    val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(RESOURCE_PATH)
    val validator = MetadataValidator(methodRegistry)
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
    val descriptors: List<MethodDescriptor> = engine.descriptors(AcmeService::class)
    descriptors.forEach { descriptor: MethodDescriptor ->
        println("METHOD: ${descriptor.id}")
        descriptor.parameters.forEach { param: ParamDescriptor ->
            println(" name=${param.name}, label=${param.label}, type=${param.type}")
        }
    }
}

fun runGuiStyleInstance(engine: ReflectionEngine) {
    val instance = AcmeService()
    val methodId: MethodId = MethodId.from(AcmeService::class, "personAddress", Person::class)
    val result: Any? = engine.invoke(methodId, instance, personValue())
    println("-----------> runGuiStyleInstance: $result")
}

fun runGuiStyleStatic(engine: ReflectionEngine) {
    val methodId: MethodId = MethodId.from(Math::class, "max", Int::class, Int::class)
    val result: Any? = engine.invoke(methodId, scalar(10), scalar(20))
    println("-----------> runGuiStyleStatic: $result")
}

fun runKotlinTopLevel(engine: ReflectionEngine) {
    val methodId: MethodId = MethodId.from(::add.javaMethod!!)
    val result: Any? = engine.invoke(methodId, scalar(10), scalar(20))
    println("-----------> runTopLevelFunction: $result")
}

private fun personValue(name: String = "Alice", age: Int = 25): Value.Record =
    Values.record(
        Person::class,
        "name" to scalar(name),
        "age" to scalar(age),
        "address" to Values.record(
            Address::class,
            "number" to scalar(2),
            "street" to scalar("Smith st"),
            "zipCode" to scalar("2321")
        )
    )
