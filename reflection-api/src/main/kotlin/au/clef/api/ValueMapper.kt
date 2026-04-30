package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.MapEntryDto
import au.clef.api.model.Value
import au.clef.api.model.ValueDto

sealed class ResolvedType {
    data class Scalar(val type: Class<*>) : ResolvedType()
    data class Structured(val type: Class<*>) : ResolvedType()
}

interface ClassResolver {
    fun resolve(typeName: String): ResolvedType
}

class ValueMapper(private val classResolver: ClassResolver) {

    fun toEngineValue(dto: ValueDto): Value = when (dto) {
        is ValueDto.Scalar    -> Value.Scalar(dto.value)
        is ValueDto.ListValue  -> Value.ListValue(dto.items.map(::toEngineValue))
        is ValueDto.Null       -> Value.Null
        is ValueDto.MapValue   -> Value.MapValue(dto.entries.map { it.toEntry() })
        is ValueDto.Record     -> mapRecord(dto)
    }

    private fun mapRecord(dto: ValueDto.Record): Value.Record {
        val resolved = classResolver.resolve(dto.type)
        if (resolved !is ResolvedType.Structured) {
            throw IllegalArgumentException("Type ${dto.type} is scalar-like and cannot be a record")
        }

        return Value.Record(
            type = resolved.type,
            fields = dto.fields.mapValues { (_, v) -> toEngineValue(v) }
        )
    }

    private fun MapEntryDto.toEntry() = MapEntry(
        key = toEngineValue(key),
        value = toEngineValue(value)
    )
}