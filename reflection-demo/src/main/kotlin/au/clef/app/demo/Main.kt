package au.clef.app.demo

import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.ExposedTarget
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.*
import au.clef.engine.model.Values.scalar
import au.clef.engine.registry.ReflectionRegistry
import au.clef.metadata.*
import au.clef.metadata.model.MetadataRoot
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaMethod

private val PERSON_ADDRESS_METHOD_ID = MethodId.from(AcmeService::class, "personAddress", Person::class)

private val STATIC_JAVA_MATH_MAX_METHOD_ID = MethodId.from(Math::class, "max", Int::class, Int::class)

private val KOTLIN_ADD_METHOD_ID = MethodId.from(::add.javaMethod!!)

private val acmeService = AcmeService()


private val targets: List<ExposedTarget> = listOf(
    ExposedTarget.InstanceMethod.from(
        obj = acmeService,
        methodName = "personAddress",
        Person::class
    ),
//    ExposedTarget.Instance(obj = acmeService),
    ExposedTarget.StaticMethod.from(::add),
    ExposedTarget.StaticMethod.from(Math::class, "max", Int::class, Int::class)
)

private val targetSupportingTypes: List<KClass<*>> = listOf(Person::class, Address::class)

private const val METADATA_RESOURCE_PATH = "/config/method-metadata.json"

private val outputFile = File("reflection-demo/src/main/resources")
    .resolve(METADATA_RESOURCE_PATH.removePrefix("/"))

private val reflectionRegistry: ReflectionRegistry = ReflectionRegistry(targets, targetSupportingTypes)

private val metadataRegistry = DescriptorMetadataRegistry(MetadataLoader.fromResourceOrEmpty(METADATA_RESOURCE_PATH))

private val engine: ReflectionEngine =
    ReflectionEngine(reflectionRegistry = reflectionRegistry, metadataRegistry = metadataRegistry)

fun main() {
    generateMetadata()
//    validateMetadata()
    showAllDescriptors()
    runInstanceMethodOnServiceInstance()
    runJavaStaticMethod()
    runKotlinTopLevelMethod()
}

private fun generateMetadata() {
    val metadata: MetadataRoot = MetadataGenerator(reflectionRegistry).generate()
    MetadataWriter.writeToFile(metadata, outputFile)
    println("Metadata written to: ${outputFile.absolutePath}")
    println(MetadataWriter.toJson(metadata))
}

// todo fix it should cgeck per method not whole class
private fun validateMetadata() {
    val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(METADATA_RESOURCE_PATH)
    val issues: List<ValidationIssue> = MetadataValidator(reflectionRegistry).validate(metadata)
    if (issues.isEmpty()) {
        println("Metadata valid")
        return
    }

    issues.forEach { issue ->
        println("[${issue.severity}] ${issue.location} - ${issue.message}")
    }
}

private fun showAllDescriptors() {
    val descriptors: List<MethodDescriptor> = engine.descriptors(AcmeService::class)
    descriptors.forEach { descriptor ->
        println("METHOD: ${descriptor.id}")
        descriptor.parameters.forEach { param: ParamDescriptor ->
            println(" name=${param.name}, label=${param.label}, type=${param.type}")
        }
    }
}

private fun runInstanceMethodOnServiceInstance() {
    val result = engine.invokeInstance(PERSON_ADDRESS_METHOD_ID, acmeService, personValue())
    println("-----------> runInstanceMethodOnServiceInstance: $result")
}

private fun runJavaStaticMethod() {
    val result = engine.invokeStatic(STATIC_JAVA_MATH_MAX_METHOD_ID, scalar(10), scalar(20))
    println("-----------> runJavaStaticMethod: $result")
}

private fun runKotlinTopLevelMethod() {
    val result = engine.invokeStatic(KOTLIN_ADD_METHOD_ID, scalar(10), scalar(20))
    println("-----------> runKotlinTopLevelMethod: $result")
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