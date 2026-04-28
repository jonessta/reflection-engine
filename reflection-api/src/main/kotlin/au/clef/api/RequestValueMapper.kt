package au.clef.api

import au.clef.api.model.ValueDto

class RequestValueMapper(
    classResolver: ClassResolver,
    userDefinedScalarConverters: List<ScalarConverter<out Any>> = emptyList()
) {
    private val valueMapper = ValueMapper(classResolver)
    private val typeConverter = TypeConverter(userDefinedScalarConverters)

    fun materialize(dto: ValueDto, targetType: Class<*>): Any? =
        typeConverter.materialize(valueMapper.toEngineValue(dto), targetType)
}