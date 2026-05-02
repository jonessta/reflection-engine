package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.ScalarValue
import au.clef.api.model.Value
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Path
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class ResponseValueMapper(
    private val scalarRegistry: ScalarTypeRegistry
) {

    fun toValue(value: Any?): Value =
        when {
            value == null -> Value.Null
            value is Value -> value

            value is Map<*, *> -> {
                Value.MapValue(
                    entries = value.entries.map { entry: Map.Entry<*, *> ->
                        MapEntry(
                            key = toValue(entry.key),
                            value = toValue(entry.value)
                        )
                    }
                )
            }

            value is Iterable<*> && value !is Path -> {
                Value.ListValue(
                    items = value.map { item: Any? -> toValue(item) }
                )
            }

            value::class.java.isArray -> {
                Value.ListValue(
                    items = reflectArray(value)
                )
            }

            value is Enum<*> -> {
                Value.Scalar(ScalarValue.StringValue(value.name))
            }

            else -> {
                encodeScalar(value) ?: toRecord(value)
            }
        }

    private fun toRecord(value: Any): Value.Record {
        val clazz: Class<*> = value::class.java

        val kotlinFields: Map<String, Value> =
            clazz.kotlin.memberProperties.associate { property: KProperty1<out Any, *> ->
                property.isAccessible = true
                property.name to toValue(property.getter.call(value))
            }

        val fields: Map<String, Value> =
            if (kotlinFields.isNotEmpty()) {
                kotlinFields
            } else {
                allFields(clazz).associate { field: Field ->
                    field.name to toValue(field.get(value))
                }
            }

        return Value.Record(
            type = clazz,
            fields = fields
        )
    }

    private fun reflectArray(array: Any): List<Value> {
        val length: Int = java.lang.reflect.Array.getLength(array)

        return (0 until length).map { index: Int ->
            toValue(java.lang.reflect.Array.get(array, index))
        }
    }

    private fun encodeScalar(value: Any): Value.Scalar? =
        scalarRegistry.encoderFor(value)?.let { converter: ScalarConverter<Any> ->
            Value.Scalar(converter.encode(value))
        }

    private fun allFields(clazz: Class<*>): List<Field> =
        generateSequence(clazz) { current: Class<*> ->
            current.superclass
        }
            .takeWhile { current: Class<*> -> current != Any::class.java }
            .flatMap { current: Class<*> -> current.declaredFields.asSequence() }
            .filter { field: Field ->
                !Modifier.isStatic(field.modifiers) && !field.isSynthetic
            }
            .onEach { field: Field ->
                field.isAccessible = true
            }
            .toList()
}