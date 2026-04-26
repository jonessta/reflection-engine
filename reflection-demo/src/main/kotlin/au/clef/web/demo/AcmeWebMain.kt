package au.clef.web.demo

import au.clef.app.demo.AcmeDemoConfig
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.ExposedTarget
import au.clef.engine.model.MethodId
import au.clef.web.ReflectionServiceApi
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

val acmeService = AcmeService()

val exposeOnlyMethods = ReflectionServiceApi(
    targets = listOf(
        // personName method on instance
        ExposedTarget.InstanceMethod(acmeService, MethodId.from(AcmeService::class, "personName", Person::class)),
        // Kotlin file add function
        ExposedTarget.StaticMethod.from(::add),
        // Method only static "max" method java Math class
        ExposedTarget.StaticMethod.from(Math::class, "max", Int::class, Int::class)
    ),
    targetSupportingTypes = AcmeDemoConfig.targetSupportingTypes,
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

val exposeStaticClass = ReflectionServiceApi(
    // Expose all Math methods
    target = ExposedTarget.StaticClass(Math::class),
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

val exposeOneService = ReflectionServiceApi(
    // id is not necessary but giving it meaningfull name for UI
//    target = ExposedTarget.Instance(acmeService, id = "acmeService"),
    target = ExposedTarget.Instance(acmeService),
    targetSupportingTypes = AcmeDemoConfig.targetSupportingTypes,
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

fun main() {
    WebServer(exposeOnlyMethods, WebServerConfig()).start()
}
