package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.Value
import au.clef.api.model.ValueDto

sealed class ResolvedType {
    data class Scalar(val type: Class<*>) : ResolvedType()
    data class Structured(val type: Class<*>) : ResolvedType()
}

interface ClassResolver {
    fun resolve(typeName: String): ResolvedType
}

class ValueMapper(
    private val classResolver: ClassResolver
) {
    fun toEngineValue(dto: ValueDto): Value =
        when (dto) {
            is ValueDto.Scalar ->
                Value.Scalar(dto.value)

            is ValueDto.Record -> {
                when (val resolvedType = classResolver.resolve(dto.type)) {
                    is ResolvedType.Structured ->
                        Value.Record(
                            type = resolvedType.type,
                            fields = dto.fields.mapValues { (_, valueDto) ->
                                toEngineValue(valueDto)
                            }
                        )

                    is ResolvedType.Scalar ->
                        throw IllegalArgumentException(
                            "Type ${dto.type} is scalar-like and must not be sent as a record"
                        )
                }
            }

            is ValueDto.ListValue ->
                Value.ListValue(dto.items.map(::toEngineValue))

            is ValueDto.MapValue ->
                Value.MapValue(
                    dto.entries.map { entry ->
                        MapEntry(
                            key = toEngineValue(entry.key),
                            value = toEngineValue(entry.value)
                        )
                    }
                )

            ValueDto.Null ->
                Value.Null
        }
}