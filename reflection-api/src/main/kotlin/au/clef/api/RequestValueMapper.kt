package au.clef.api

import au.clef.api.model.ValueDto

class RequestValueMapper(
    classResolver: ClassResolver,
    scalarTypeRegistry: ScalarTypeRegistry
) {
    private val valueMapper = ValueMapper(classResolver)
    private val typeConverter = TypeConverter(scalarTypeRegistry)

    fun materialize(dto: ValueDto, targetType: Class<*>): Any? =
        typeConverter.materialize(valueMapper.toEngineValue(dto), targetType)

    fun isScalarLike(targetType: Class<*>): Boolean =
        typeConverter.supportsScalarTarget(targetType)
}