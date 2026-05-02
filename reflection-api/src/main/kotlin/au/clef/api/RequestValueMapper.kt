package au.clef.api

import au.clef.api.model.Value

class RequestValueMapper(
    scalarTypeRegistry: ScalarTypeRegistry
) {
    private val typeConverter: TypeConverter = TypeConverter(scalarTypeRegistry)

    fun materialize(value: Value, targetType: Class<*>): Any? =
        typeConverter.materialize(value, targetType)

    fun isScalarLike(targetType: Class<*>): Boolean =
        typeConverter.supportsScalarTarget(targetType)
}