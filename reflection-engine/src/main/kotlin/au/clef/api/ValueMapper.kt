package au.clef.api

import au.clef.api.model.ValueDto
import au.clef.engine.model.Value

interface ClassResolver {
    fun resolve(typeName: String): Class<*>
}

class ValueMapper(
    private val instanceRegistry: au.clef.api.InstanceRegistry,
    private val classResolver: au.clef.api.ClassResolver
) {
    fun toEngineValue(dto: au.clef.api.model.ValueDto): au.clef.engine.model.Value = when (dto) {
        is au.clef.api.model.ValueDto.Scalar -> _root_ide_package_.au.clef.engine.model.Value.Scalar(dto.value)
        is au.clef.api.model.ValueDto.InstanceRef -> _root_ide_package_.au.clef.engine.model.Value.Instance(instanceRegistry.get(dto.id))
        is au.clef.api.model.ValueDto.Record -> _root_ide_package_.au.clef.engine.model.Value.Record(
            type = classResolver.resolve(dto.type),
            fields = dto.fields.mapValues { (_, valueDto: au.clef.api.model.ValueDto) -> toEngineValue(valueDto) }
        )

        is au.clef.api.model.ValueDto.ListValue -> _root_ide_package_.au.clef.engine.model.Value.ListValue(dto.items.map(::toEngineValue))
        is au.clef.api.model.ValueDto.MapValue -> _root_ide_package_.au.clef.engine.model.Value.MapValue(dto.entries.mapValues { (_, v: au.clef.api.model.ValueDto) -> toEngineValue(v) })
        _root_ide_package_.au.clef.api.model.ValueDto.Null -> _root_ide_package_.au.clef.engine.model.Value.Null
    }
}