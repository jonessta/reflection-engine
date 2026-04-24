package au.clef.api

import au.clef.api.model.ValueDto
import au.clef.engine.model.Value

interface ClassResolver {
    fun resolve(typeName: String): Class<*>
}

class ValueMapper(
    private val instanceRegistry: InstanceRegistry,
    private val classResolver: ClassResolver
) {
    fun toEngineValue(dto: ValueDto): Value = when (dto) {
        is ValueDto.Scalar -> Value.Scalar(dto.value)
        is ValueDto.InstanceRef -> Value.Instance(instanceRegistry.get(dto.id))
        is ValueDto.Record -> Value.Record(
            type = classResolver.resolve(dto.type),
            fields = dto.fields.mapValues { (_, valueDto: ValueDto) -> toEngineValue(valueDto) }
        )

        is ValueDto.ListValue -> Value.ListValue(dto.items.map(::toEngineValue))
        is ValueDto.MapValue -> Value.MapValue(dto.entries.mapValues { (_, v: ValueDto) -> toEngineValue(v) })
        ValueDto.Null -> Value.Null
    }
}