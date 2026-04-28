package au.clef.api

import au.clef.api.model.MapEntryDto
import au.clef.api.model.ValueDto
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Modifier
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class ResponseValueMapper(
    private val scalarEncoders: List<ScalarValueEncoder> = DefaultScalarValueEncoders.all
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

            else -> {
                val scalar = scalarEncoders
                    .firstOrNull { it.canEncode(value) }
                    ?.encode(value)

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
}

interface ScalarValueEncoder {
    fun canEncode(value: Any): Boolean
    fun encode(value: Any): JsonPrimitive
}

class SimpleScalarValueEncoder(
    private val predicate: (Any) -> Boolean,
    private val encoder: (Any) -> JsonPrimitive
) : ScalarValueEncoder {

    override fun canEncode(value: Any): Boolean =
        predicate(value)

    override fun encode(value: Any): JsonPrimitive =
        encoder(value)
}

object DefaultScalarValueEncoders {
    val all: List<ScalarValueEncoder> = listOf(
        SimpleScalarValueEncoder(
            predicate = { it is String },
            encoder = { JsonPrimitive(it as String) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is Number },
            encoder = { JsonPrimitive(it as Number) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is Boolean },
            encoder = { JsonPrimitive(it as Boolean) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is Char },
            encoder = { JsonPrimitive(it.toString()) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is Enum<*> },
            encoder = { JsonPrimitive((it as Enum<*>).name) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is UUID },
            encoder = { JsonPrimitive(it.toString()) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is URI },
            encoder = { JsonPrimitive(it.toString()) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is URL },
            encoder = { JsonPrimitive(it.toString()) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is Path },
            encoder = { JsonPrimitive(it.toString()) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is Instant },
            encoder = { JsonPrimitive(it.toString()) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is LocalDate },
            encoder = { JsonPrimitive(it.toString()) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is LocalDateTime },
            encoder = { JsonPrimitive(it.toString()) }
        ),
        SimpleScalarValueEncoder(
            predicate = { it is LocalTime },
            encoder = { JsonPrimitive(it.toString()) }
        )
    )
}