package au.clef.api

import au.clef.api.model.ValueDto
import au.clef.engine.model.MapEntry
import au.clef.engine.model.Value

interface ClassResolver {
    fun resolve(typeName: String): Class<*>
}

class ValueMapper(
    private val classResolver: ClassResolver
) {
    fun toEngineValue(dto: ValueDto): Value =
        when (dto) {
            is ValueDto.Scalar ->
                Value.Scalar(dto.value)

            is ValueDto.Record ->
                Value.Record(
                    type = classResolver.resolve(dto.type),
                    fields = dto.fields.mapValues { (_, valueDto) ->
                        toEngineValue(valueDto)
                    }
                )

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