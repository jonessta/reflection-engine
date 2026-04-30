package au.clef.api

import au.clef.api.model.MapEntryDto
import au.clef.api.model.ValueDto
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Path
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class ResponseValueMapper(
    private val scalarRegistry: ScalarTypeRegistry
) {

    fun toDtoValue(value: Any?): ValueDto =
        when {
            value == null -> {
                ValueDto.Null
            }

            value is Map<*, *> -> {
                ValueDto.MapValue(
                    value.entries.map { entry: Map.Entry<*, *> ->
                        MapEntryDto(
                            key = toDtoValue(entry.key),
                            value = toDtoValue(entry.value)
                        )
                    }
                )
            }

            value is Iterable<*> && value !is Path -> {
                ValueDto.ListValue(
                    value.map { item: Any? -> toDtoValue(item) }
                )
            }

            value::class.java.isArray -> {
                ValueDto.ListValue(reflectArray(value))
            }

            value is Enum<*> -> {
                ValueDto.Scalar(JsonPrimitive(value.name))
            }

            else -> {
                encodeScalar(value) ?: toRecord(value)
            }
        }

    private fun toRecord(value: Any): ValueDto.Record {
        val clazz: Class<*> = value::class.java

        val kotlinFields: Map<String, ValueDto> =
            clazz.kotlin.memberProperties
                .associate { property: KProperty1<out Any, *> ->
                    property.isAccessible = true
                    property.name to toDtoValue(property.getter.call(value))
                }

        val fields: Map<String, ValueDto> =
            if (kotlinFields.isNotEmpty()) {
                kotlinFields
            } else {
                allFields(clazz).associate { field: Field ->
                    field.name to toDtoValue(field.get(value))
                }
            }

        return ValueDto.Record(
            type = clazz.name,
            fields = fields
        )
    }

    private fun reflectArray(array: Any): List<ValueDto> {
        val length: Int = java.lang.reflect.Array.getLength(array)

        return (0 until length).map { index: Int ->
            toDtoValue(java.lang.reflect.Array.get(array, index))
        }
    }

    private fun encodeScalar(value: Any): ValueDto.Scalar? =
        scalarRegistry.encoderFor(value)?.let { converter: ScalarConverter<Any> ->
            ValueDto.Scalar(converter.encode(value))
        }

    private fun allFields(clazz: Class<*>): List<Field> =
        generateSequence(clazz) { current: Class<*> -> current.superclass }
            .takeWhile { current: Class<*> -> current != Any::class.java }
            .flatMap { current: Class<*> -> current.declaredFields.asSequence() }
            .filter { field: Field -> !Modifier.isStatic(field.modifiers) && !field.isSynthetic }
            .onEach { field: Field -> field.isAccessible = true }
            .toList()
}