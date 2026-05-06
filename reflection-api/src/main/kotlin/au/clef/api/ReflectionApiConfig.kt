package au.clef.api

import au.clef.engine.ReflectionConfig

data class ReflectionApiConfig(
    val reflectionConfig: ReflectionConfig,
    val metadataResourcePath: String? = null,
    val userDefinedScalarConverters: List<ScalarConverter<out Any>> = emptyList()
) {

    val scalarTypeRegistry: ScalarTypeRegistry = ScalarTypeRegistry(userDefinedScalarConverters)
}

class ReflectionApiConfigBuilder(private val reflectionConfig: ReflectionConfig) {

    private val userDefinedScalarConverters = mutableListOf<ScalarConverter<out Any>>()

    private var metadataResourcePath: String? = null

    fun scalarConverters(vararg converters: ScalarConverter<out Any>): ReflectionApiConfigBuilder =
        apply { userDefinedScalarConverters += converters }

    fun metadataResourcePath(path: String?): ReflectionApiConfigBuilder =
        apply { metadataResourcePath = path }

    fun build(): ReflectionApiConfig =
        ReflectionApiConfig(
            reflectionConfig = reflectionConfig,
            metadataResourcePath = metadataResourcePath,
            userDefinedScalarConverters = userDefinedScalarConverters.toList()
        )
}

fun reflectionApiConfig(reflectionConfig: ReflectionConfig): ReflectionApiConfigBuilder =
    ReflectionApiConfigBuilder(reflectionConfig)
