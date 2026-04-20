package au.clef.api

import au.clef.api.model.ValueDto
import au.clef.engine.model.Value

class ValueMapper(private val instanceRegistry: InstanceRegistry) {

    fun toEngineValue(dto: ValueDto): Value = when (dto) {
        is ValueDto.Primitive -> Value.Primitive(dto.value)

        is ValueDto.Instance -> Value.Instance(instanceRegistry.get(dto.id))

        is ValueDto.Object -> Value.Object(
            type = Class.forName(dto.type),
            fields = dto.fields.mapValues { (_, valueDto: ValueDto) -> toEngineValue(valueDto) })
    }
}