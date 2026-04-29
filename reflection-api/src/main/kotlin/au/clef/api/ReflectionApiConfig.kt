package au.clef.api

import au.clef.engine.ReflectionConfig

data class ReflectionApiConfig(
    val reflectionConfig: ReflectionConfig,
    val userDefinedScalarConverters: List<ScalarConverter<out Any>> = emptyList()
)