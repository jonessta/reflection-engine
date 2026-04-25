package au.clef.api

import au.clef.api.model.ValueDto
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Modifier

class ResponseValueMapper(
    private val classResolver: ClassResolver? = null
) {

    fun toDtoValue(value: Any?): ValueDto =
        when (value) {
            null -> ValueDto.Null

            is String -> ValueDto.Scalar(JsonPrimitive(value))
            is Number -> ValueDto.Scalar(JsonPrimitive(value))
            is Boolean -> ValueDto.Scalar(JsonPrimitive(value))
            is Char -> ValueDto.Scalar(JsonPrimitive(value.toString()))

            is Map<*, *> -> {
                val entries = value.entries.associate { (k, v) ->
                    require(k is String) { "Only string map keys are supported" }
                    k to toDtoValue(v)
                }
                ValueDto.MapValue(entries)
            }

            is Iterable<*> -> {
                ValueDto.ListValue(value.map(::toDtoValue))
            }

            else -> toRecord(value)
        }

    private fun toRecord(value: Any): ValueDto.Record {
        val clazz = value.javaClass

        val fields = clazz.declaredFields
            .filterNot { field ->
                Modifier.isStatic(field.modifiers) || field.isSynthetic
            }
            .associate { field ->
                field.isAccessible = true
                field.name to toDtoValue(field.get(value))
            }

        return ValueDto.Record(
            type = clazz.name,
            fields = fields
        )
    }
}