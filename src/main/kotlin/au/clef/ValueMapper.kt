package au.clef

class ValueMapper(
    private val instanceRegistry: InstanceRegistry
) {
    fun toEngineValue(dto: au.clef.api.ValueDto): Value =
        when (dto) {
            is au.clef.api.ValueDto.Primitive ->
                Value.Primitive(dto.value)

            is au.clef.api.ValueDto.Instance ->
                Value.Instance(instanceRegistry.get(dto.id))

            is au.clef.api.ValueDto.Object ->
                Value.Object(
                    type = Class.forName(dto.type),
                    fields = dto.fields.mapValues { (_, valueDto) -> toEngineValue(valueDto) }
                )
        }
}