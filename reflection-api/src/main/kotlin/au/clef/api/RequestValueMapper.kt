package au.clef.api

import au.clef.api.model.ValueDto

class RequestValueMapper(
    private val valueMapper: ValueMapper,
    private val typeConverter: TypeConverter = TypeConverter()
) {

    fun materialize(dto: ValueDto, targetType: Class<*>): Any? =
        typeConverter.materialize(valueMapper.toEngineValue(dto), targetType)
}