package au.clef.api

import au.clef.engine.ReflectionConfig

data class ReflectionApiConfig(
    val reflectionConfig: ReflectionConfig,
    val userDefinedScalarConverters: List<ScalarConverter<out Any>> = emptyList()
)

class ReflectionApiConfigBuilder(
    private val reflectionConfig: ReflectionConfig
) {
    private val userDefinedScalarConverters = mutableListOf<ScalarConverter<out Any>>()

    fun scalarConverter(converter: ScalarConverter<out Any>): ReflectionApiConfigBuilder =
        apply { userDefinedScalarConverters += converter }

    fun scalarConverters(vararg converters: ScalarConverter<out Any>): ReflectionApiConfigBuilder =
        apply { userDefinedScalarConverters += converters }

    fun build(): ReflectionApiConfig =
        ReflectionApiConfig(
            reflectionConfig = reflectionConfig,
            userDefinedScalarConverters = userDefinedScalarConverters.toList()
        )
}

fun reflectionApiConfig(reflectionConfig: ReflectionConfig): ReflectionApiConfigBuilder =
    ReflectionApiConfigBuilder(reflectionConfig)