package au.clef.app.demo

import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Address
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.*
import au.clef.engine.model.*
import au.clef.engine.model.Values.scalar
import au.clef.metadata.*
import au.clef.metadata.model.MetadataRoot
import java.io.File
import kotlin.reflect.jvm.javaMethod

private val PERSON_ADDRESS_METHOD_ID = MethodId.from(AcmeService::class, "personAddress", Person::class)

private val STATIC_JAVA_MATH_MAX_METHOD_ID = MethodId.from(Math::class, "max", Int::class, Int::class)

private val KOTLIN_ADD_METHOD_ID = MethodId.from(::add.javaMethod!!)

private val acmeService = AcmeService()

private val demoDefinition = ReflectionAppDefinition(
    targets = listOf(
        ExposedTarget.Instance("acmeService", acmeService),
        ExposedTarget.StaticClass(Math::class),
        ExposedTarget.StaticMethod(KOTLIN_ADD_METHOD_ID)
    ),
    targetSupportingTypes = listOf(Person::class, Address::class),
    metadataResourcePath = "/config/method-metadata.json"
)

private val outputFile: File? =
    demoDefinition.metadataResourcePath
        ?.removePrefix("/")
        ?.let { File("src/main/resources").resolve(it) }

private val runtime: ReflectionRuntime = createReflectionRuntime(demoDefinition)

fun main() {
    generateMetadata()
    validateMetadata()
    showAllDescriptors()
    runInstanceMethodOnServiceInstance()
    runJavaStaticMethod()
    runKotlinTopLevelMethod()
}

private fun generateMetadata() {
    val file = outputFile ?: return
    val metadata: MetadataRoot = MetadataGenerator(runtime.reflectionRegistry).generate()
    MetadataWriter.writeToFile(metadata, file)
    println("Metadata written to: ${file.absolutePath}")
    println(MetadataWriter.toJson(metadata))
}

private fun validateMetadata() {
    val path = demoDefinition.metadataResourcePath ?: return
    val metadata: MetadataRoot = MetadataLoader.fromResourceOrEmpty(path)
    val issues: List<ValidationIssue> = MetadataValidator(runtime.reflectionRegistry).validate(metadata)
    if (issues.isEmpty()) {
        println("Metadata valid")
        return
    }

    issues.forEach { issue ->
        println("[${issue.severity}] ${issue.location} - ${issue.message}")
    }
}

private fun showAllDescriptors() {
    val descriptors: List<MethodDescriptor> = runtime.engine.descriptors(AcmeService::class)
    descriptors.forEach { descriptor ->
        println("METHOD: ${descriptor.id}")
        descriptor.parameters.forEach { param: ParamDescriptor ->
            println(" name=${param.name}, label=${param.label}, type=${param.type}")
        }
    }
}

private fun runInstanceMethodOnServiceInstance() {
    val result = runtime.engine.invoke(PERSON_ADDRESS_METHOD_ID, acmeService, person())
    println("-----------> runGuiStyleInstance: $result")
}

private fun runJavaStaticMethod() {
    val result = runtime.engine.invoke(STATIC_JAVA_MATH_MAX_METHOD_ID, scalar(10), scalar(20))
    println("-----------> runGuiStyleStatic: $result")
}

private fun runKotlinTopLevelMethod() {
    val result = runtime.engine.invoke(KOTLIN_ADD_METHOD_ID, scalar(10), scalar(20))
    println("-----------> runTopLevelFunction: $result")
}

private fun person(name: String = "Alice", age: Int = 25): Value.Record =
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