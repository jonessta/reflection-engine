package au.clef.api

import au.clef.api.model.MapEntryDto
import au.clef.api.model.ValueDto
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Modifier
import java.nio.file.Path

class ResponseValueMapper(
    userDefinedScalarConverters: List<ScalarConverter<out Any>> = emptyList()
) {
    private val scalarConverters: List<ScalarConverter<out Any>> =
        userDefinedScalarConverters + DefaultScalarConverters.all

    fun toDtoValue(value: Any?): ValueDto =
        when {
            value == null ->
                ValueDto.Null

            value is Map<*, *> ->
                ValueDto.MapValue(
                    value.entries.map { (key, entryValue) ->
                        MapEntryDto(
                            key = toDtoValue(key),
                            value = toDtoValue(entryValue)
                        )
                    }
                )

            value is Array<*> ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is IntArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is LongArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is DoubleArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is FloatArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is ShortArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is ByteArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is BooleanArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is CharArray ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is Iterable<*> && value !is Path ->
                ValueDto.ListValue(value.map(::toDtoValue))

            value is Enum<*> ->
                ValueDto.Scalar(JsonPrimitive(value.name))

            else -> {
                val scalar = encodeScalar(value)
                if (scalar != null) {
                    ValueDto.Scalar(scalar)
                } else {
                    toRecord(value)
                }
            }
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

    private fun encodeScalar(value: Any): JsonPrimitive? {
        val converter = scalarConverters.firstOrNull { it.type.javaObjectType.isInstance(value) } ?: return null
        @Suppress("UNCHECKED_CAST")
        return (converter as ScalarConverter<Any>).encode(value)
    }
}