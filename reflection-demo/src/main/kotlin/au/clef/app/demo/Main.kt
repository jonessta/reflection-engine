package au.clef.app.demo

import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.MethodSource.InstanceMethod
import au.clef.engine.MethodSource.StaticMethod
import au.clef.engine.ReflectionConfig
import au.clef.engine.ReflectionEngine
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.ParamDescriptor
import au.clef.engine.reflectionConfig
import au.clef.engine.registry.MethodSourceRegistry
import au.clef.metadata.*
import au.clef.metadata.model.MetadataRoot
import java.io.File
import kotlin.reflect.jvm.javaMethod

private val PERSON_ADDRESS_METHOD_ID = MethodId.from(AcmeService::class, "personAddress", Person::class)

private val STATIC_JAVA_MATH_MAX_METHOD_ID = MethodId.from(Math::class, "max", Int::class, Int::class)

private val KOTLIN_ADD_METHOD_ID = MethodId.from(::add.javaMethod!!)

private val acmeService = AcmeService()

private const val METADATA_RESOURCE_PATH = "/config/method-metadata.json"

private val outputFile = File("reflection-demo/src/main/resources")
    .resolve(METADATA_RESOURCE_PATH.removePrefix("/"))

private val reflectionConfig: ReflectionConfig = reflectionConfig(
    InstanceMethod(
        instance = acmeService,
        instanceDescription = "ACME Service",
        methodName = "personAddress",
        Person::class
    ),
    StaticMethod(::add),
    StaticMethod(Math::class, "max", Int::class, Int::class)
)
    .supportingTypes(Person::class, Address::class)
    .metadataResourcePath(METADATA_RESOURCE_PATH)
    .build()

private val reflectionRegistry = MethodSourceRegistry(
    methodSources = reflectionConfig.methodSources,
    methodSupportingTypes = reflectionConfig.methodSupportingTypes,
    inheritanceLevel = reflectionConfig.inheritanceLevel
)

private val metadataRegistry = DescriptorMetadataRegistry(MetadataLoader.fromResourceOrEmpty(METADATA_RESOURCE_PATH))

private val engine = ReflectionEngine(reflectionConfig = reflectionConfig, metadataRegistry = metadataRegistry)

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

// todo fix it should check per method not whole class
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
    val result = engine.invokeInstance(PERSON_ADDRESS_METHOD_ID, acmeService, person())
    println("-----------> runInstanceMethodOnServiceInstance: $result")
}

private fun runJavaStaticMethod() {
    val result = engine.invokeStatic(STATIC_JAVA_MATH_MAX_METHOD_ID, 10, 10)
    println("-----------> runJavaStaticMethod: $result")
}

private fun runKotlinTopLevelMethod() {
    val result = engine.invokeStatic(KOTLIN_ADD_METHOD_ID, 10, 10)
    println("-----------> runKotlinTopLevelMethod: $result")
}

private fun person(name: String = "Alice", age: Int = 25): Person =
    Person(
        name = name,
        age = age,
        address = Address(
            number = 2,
            street = "Smith st",
            zipCode = "2321"
        )
    )