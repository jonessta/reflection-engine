package au.clef.api

import au.clef.api.model.MapEntryDto
import au.clef.api.model.ValueDto
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Modifier

class ResponseValueMapper {

    fun toDtoValue(value: Any?): ValueDto =
        when (value) {
            null ->
                ValueDto.Null

            is String ->
                ValueDto.Scalar(JsonPrimitive(value))

            is Number ->
                ValueDto.Scalar(JsonPrimitive(value))

            is Boolean ->
                ValueDto.Scalar(JsonPrimitive(value))

            is Char ->
                ValueDto.Scalar(JsonPrimitive(value.toString()))

            is Array<*> ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is IntArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is LongArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is DoubleArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is FloatArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is ShortArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is ByteArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is BooleanArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is CharArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is Map<*, *> ->
                ValueDto.MapValue(
                    value.entries.map { (k, v) ->
                        MapEntryDto(key = toDtoValue(k), value = toDtoValue(v))
                    }
                )

            is Iterable<*> ->
                ValueDto.ListValue(value.map(::toDtoValue))

            else ->
                toRecord(value)
        }

    private fun toRecord(value: Any): ValueDto.Record {
        val clazz = value.javaClass

        val fields: Map<String, ValueDto> = clazz.fields
            .filterNot { field ->
                Modifier.isStatic(field.modifiers) || field.isSynthetic
            }
            .associate { field ->
                field.name to toDtoValue(field.get(value))
            }

        return ValueDto.Record(
            type = clazz.name,
            fields = fields
        )
    }
}