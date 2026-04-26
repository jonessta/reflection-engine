package au.clef.web.demo

import au.clef.app.demo.AcmeDemoConfig
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.MethodSource
import au.clef.engine.model.MethodId
import au.clef.web.ReflectionServiceApi
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

val acmeService = AcmeService()

val exposeMethodsOnInstanceAndStatic = ReflectionServiceApi(
    targets = listOf(
        // personName method on acmeService instance
        MethodSource.InstanceMethod(acmeService, id="acmeService", methodId = MethodId.from(AcmeService::class, "personName", Person::class)),
//        ExposedTarget.InstanceMethod(acmeService, MethodId.from(AcmeService::class, "personName", Person::class)),

        // Kotlin file add function
        MethodSource.StaticMethod.from(::add),

        // Static "max" method java Math class
        MethodSource.StaticMethod.from(Math::class, "max", Int::class, Int::class)
    ),
    targetSupportingTypes = AcmeDemoConfig.targetSupportingTypes,
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

val exposeAllMethodsOnStaticClass = ReflectionServiceApi(
    target = MethodSource.StaticClass(Math::class),
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

val exposeAllMethodsService = ReflectionServiceApi(
    target = MethodSource.Instance(acmeService, id = "myService"),
//    target = ExposedTarget.Instance(acmeService),
    targetSupportingTypes = AcmeDemoConfig.targetSupportingTypes,
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

fun main() {
    WebServer(exposeMethodsOnInstanceAndStatic, WebServerConfig()).start()
}
