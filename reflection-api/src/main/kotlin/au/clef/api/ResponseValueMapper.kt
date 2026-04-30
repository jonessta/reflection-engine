package au.clef.api

import au.clef.api.model.MapEntryDto
import au.clef.api.model.ValueDto
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Modifier
import java.nio.file.Path
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class ResponseValueMapper(
    private val scalarTypeRegistry: ScalarTypeRegistry
) {
    fun toDtoValue(value: Any?): ValueDto =
        when (value) {
            null ->
                ValueDto.Null

            is Map<*, *> -> ValueDto.MapValue(
                value.entries.map { (key, entryValue) ->
                    MapEntryDto(
                        key = toDtoValue(key),
                        value = toDtoValue(entryValue)
                    )
                }
            )

            is Array<*> -> ValueDto.ListValue(value.map(::toDtoValue))
            is IntArray -> ValueDto.ListValue(value.map(::toDtoValue))
            is LongArray -> ValueDto.ListValue(value.map(::toDtoValue))
            is DoubleArray -> ValueDto.ListValue(value.map(::toDtoValue))
            is FloatArray -> ValueDto.ListValue(value.map(::toDtoValue))
            is ShortArray -> ValueDto.ListValue(value.map(::toDtoValue))
            is ByteArray -> ValueDto.ListValue(value.map(::toDtoValue))
            is BooleanArray -> ValueDto.ListValue(value.map(::toDtoValue))
            is CharArray -> ValueDto.ListValue(value.map(::toDtoValue))
            is Iterable<*> if value !is Path ->
                ValueDto.ListValue(value.map(::toDtoValue))

            is Enum<*> -> ValueDto.Scalar(JsonPrimitive(value.name))
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
        val kClass = clazz.kotlin

        val fields: Map<String, ValueDto> =
            kClass
                .declaredMemberProperties
                .associate { property ->
                    property.isAccessible = true
                    property.name to toDtoValue(property.getter.call(value))
                }
                .ifEmpty {
                    clazz.fields
                        .filterNot { field ->
                            Modifier.isStatic(field.modifiers) || field.isSynthetic
                        }
                        .associate { field ->
                            field.name to toDtoValue(field.get(value))
                        }
                }

        return ValueDto.Record(
            type = clazz.name,
            fields = fields
        )
    }

    private fun encodeScalar(value: Any): JsonPrimitive? =
        scalarTypeRegistry.encoderFor(value)?.encode(value)
}