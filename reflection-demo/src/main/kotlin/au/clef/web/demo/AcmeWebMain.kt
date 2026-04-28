package au.clef.web.demo

import au.clef.api.ReflectionServiceApi
import au.clef.app.demo.AcmeDemoConfig
import au.clef.app.demo.model.AcmeService
import au.clef.app.demo.model.Person
import au.clef.app.demo.model.add
import au.clef.engine.MethodSource
import au.clef.web.WebServer
import au.clef.web.WebServerConfig

private val acmeServiceInstance = AcmeService()

private val exposeMethodsOnInstanceAndStatic = ReflectionServiceApi(
    methodSources = listOf(
        MethodSource.InstanceMethod.from(
            instance = acmeServiceInstance,
            instanceDescription = "ACME Service",
            methodName = "personName",
            Person::class
        ),
        MethodSource.StaticMethod.from(::add),
        MethodSource.StaticMethod.from(Math::class, "max", Int::class, Int::class),
        MethodSource.StaticMethod.from(Math::class, "min", Int::class, Int::class)
    ),
    methodSupportingTypes = AcmeDemoConfig.methodSupportingTypes,
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

private val exposeAllMethodsOnStaticClass = ReflectionServiceApi(
    methodSource = MethodSource.StaticClass(Math::class),
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH,
    userDefinedScalarConverters = emptyList()
)

private val exposeAllMethodsService = ReflectionServiceApi(
    methodSource = MethodSource.Instance(
        instance = acmeServiceInstance,
        instanceDescription = "myService"
    ),
    methodSupportingTypes = AcmeDemoConfig.methodSupportingTypes,
    metadataResourcePath = AcmeDemoConfig.METADATA_RESOURCE_PATH
)

fun main() {
    WebServer(exposeMethodsOnInstanceAndStatic, WebServerConfig()).start()
}