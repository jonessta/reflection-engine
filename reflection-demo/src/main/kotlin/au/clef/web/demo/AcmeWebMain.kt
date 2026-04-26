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

val acmeServiceInstance = AcmeService()

val exposeMethodsOnInstanceAndStatic = ReflectionServiceApi(
    methodSources = listOf(
        // personName method on acmeService instance
        MethodSource.InstanceMethod(acmeServiceInstance, instanceId="acmeService", methodId = MethodId.from(AcmeService::class, "personName", Person::class)),
//        MethodSource.InstanceMethod(acmeService, MethodId.from(AcmeService::class, "personName", Person::class)),

        // Kotlin file add function
        MethodSource.StaticMethod.from(::add),

        // Static "max" method java Math class
        MethodSource.StaticMethod.from(Math::class, "max", Int::class, Int::class)
    ),
    methodSupportingTypes = AcmeDemoConfig.methodSupportingTypes,
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

val exposeAllMethodsOnStaticClass = ReflectionServiceApi(
    methodSource = MethodSource.StaticClass(Math::class),
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

val exposeAllMethodsService = ReflectionServiceApi(
    methodSource = MethodSource.Instance(acmeServiceInstance, instanceId = "myService"),
//    methodSource = ExposedTarget.Instance(acmeService),
    methodSupportingTypes = AcmeDemoConfig.methodSupportingTypes,
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

fun main() {
    WebServer(exposeMethodsOnInstanceAndStatic, WebServerConfig()).start()
}
