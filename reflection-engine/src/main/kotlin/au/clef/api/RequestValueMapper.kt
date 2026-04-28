package au.clef.api

import au.clef.api.model.ValueDto
import au.clef.engine.convert.TypeConverter
import au.clef.engine.model.Value
import java.lang.reflect.Type

class RequestValueMapper(
    private val valueMapper: ValueMapper,
    private val typeConverter: TypeConverter = TypeConverter()
) {

    fun toEngineValue(dto: ValueDto): Value =
        valueMapper.toEngineValue(dto)

    fun materialize(dto: ValueDto, targetType: Class<*>): Any? =
        typeConverter.materialize(valueMapper.toEngineValue(dto), targetType)

    fun materialize(dto: ValueDto, targetType: Type): Any? =
        typeConverter.materialize(valueMapper.toEngineValue(dto), targetType)

    fun materializeAll(values: List<ValueDto>, targetTypes: List<Class<*>>): List<Any?> {
        require(values.size == targetTypes.size) {
            "Expected ${targetTypes.size} values, got ${values.size}"
        }

        return values.zip(targetTypes).map { (value, targetType) ->
            materialize(value, targetType)
        }
    }
}