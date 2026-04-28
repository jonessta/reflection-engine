package au.clef.api

import au.clef.api.model.MapEntry
import au.clef.api.model.Value
import au.clef.engine.ObjectConstructionException
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.*
import java.lang.reflect.Array
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class TypeConverter(
    userDefinedScalarDecoders: List<ScalarValueDecoder> = emptyList()
) {
    private val scalarDecoders: List<ScalarValueDecoder> =
        userDefinedScalarDecoders + DefaultScalarValueDecoders.all

    fun materialize(value: Value, targetType: Class<*>): Any? =
        materializeInternal(value, targetType as Type)

    fun materialize(value: Value, targetType: Type): Any? =
        materializeInternal(value, targetType)

    private fun materializeInternal(value: Value, targetType: Type): Any? {
        if (value is Value.Null) {
            val rawType = rawClassOf(targetType)
            if (rawType.isPrimitive) {
                throw TypeMismatchException(value, rawType)
            }
            return null
        }

        return when (value) {
            is Value.Scalar -> convertScalar(value.value, targetType)
            is Value.Instance -> convertInstance(value, targetType)
            is Value.Record -> convertRecord(value, targetType)
            is Value.ListValue -> convertList(value, targetType)
            is Value.MapValue -> convertMap(value, targetType)
            Value.Null -> error("Null already handled")
        }
    }

    private fun convertScalar(rawValue: Any?, targetType: Type): Any? {
        val rawTargetType = rawClassOf(targetType)

        if (rawValue == null) {
            if (rawTargetType.isPrimitive) {
                throw TypeMismatchException(Value.Scalar(null), rawTargetType)
            }
            return null
        }

        val wrappedTargetType = wrapPrimitive(rawTargetType)
        val normalizedValue = normalizeScalar(rawValue)

        if (normalizedValue == null) {
            if (rawTargetType.isPrimitive) {
                throw TypeMismatchException(Value.Scalar(null), rawTargetType)
            }
            return null
        }

        if (wrappedTargetType.isInstance(normalizedValue)) {
            return normalizedValue
        }

        scalarDecoders.firstOrNull { it.canDecode(normalizedValue, wrappedTargetType) }?.let { decoder ->
            return try {
                decoder.decode(normalizedValue, wrappedTargetType)
            } catch (e: TypeMismatchException) {
                throw e
            } catch (e: Exception) {
                throw TypeMismatchException(Value.Scalar(rawValue), rawTargetType)
            }
        }

        throw TypeMismatchException(Value.Scalar(rawValue), rawTargetType)
    }

    private fun normalizeScalar(rawValue: Any?): Any? =
        when (rawValue) {
            is JsonPrimitive -> {
                if (rawValue.isString) {
                    rawValue.content
                } else {
                    val text = rawValue.content
                    when {
                        text.equals("true", ignoreCase = true) -> true
                        text.equals("false", ignoreCase = true) -> false
                        text.toIntOrNull() != null -> text.toInt()
                        text.toLongOrNull() != null -> text.toLong()
                        text.toDoubleOrNull() != null -> text.toDouble()
                        else -> text
                    }
                }
            }

            else -> rawValue
        }

    private fun convertInstance(value: Value.Instance, targetType: Type): Any {
        val instance = value.obj
        val rawTargetType = rawClassOf(targetType)
        if (!wrapPrimitive(rawTargetType).isInstance(instance)) {
            throw TypeMismatchException(value, rawTargetType)
        }
        return instance
    }

    private fun convertRecord(value: Value.Record, targetType: Type): Any {
        val rawTargetType = rawClassOf(targetType)
        if (value.type != Any::class.java &&
            !rawTargetType.isAssignableFrom(value.type) &&
            !value.type.isAssignableFrom(rawTargetType)
        ) {
            throw TypeMismatchException(value, rawTargetType)
        }

        return buildObject(value, rawTargetType)
    }

    private fun convertList(value: Value.ListValue, targetType: Type): Any {
        val rawTargetType = rawClassOf(targetType)
        val elementType = when (targetType) {
            is ParameterizedType -> targetType.actualTypeArguments[0]
            else -> Any::class.java
        }

        val materializedItems = value.items.map { item ->
            materialize(item, elementType)
        }

        return when {
            rawTargetType.isArray -> {
                val componentType = rawTargetType.componentType
                val array = Array.newInstance(componentType, materializedItems.size)
                materializedItems.forEachIndexed { index, element ->
                    Array.set(array, index, element)
                }
                array
            }

            Set::class.java.isAssignableFrom(rawTargetType) ->
                LinkedHashSet(materializedItems)

            List::class.java.isAssignableFrom(rawTargetType) ||
                    Collection::class.java.isAssignableFrom(rawTargetType) ->
                materializedItems.toMutableList()

            Iterable::class.java.isAssignableFrom(rawTargetType) ->
                materializedItems

            else ->
                throw TypeMismatchException(value, rawTargetType)
        }
    }

    private fun convertMap(value: Value.MapValue, targetType: Type): Any {
        val rawTargetType = rawClassOf(targetType)
        if (!Map::class.java.isAssignableFrom(rawTargetType)) {
            throw TypeMismatchException(value, rawTargetType)
        }

        val keyType = when (targetType) {
            is ParameterizedType -> targetType.actualTypeArguments[0]
            else -> Any::class.java
        }

        val valueType = when (targetType) {
            is ParameterizedType -> targetType.actualTypeArguments[1]
            else -> Any::class.java
        }

        val result = LinkedHashMap<Any?, Any?>()
        value.entries.forEach { entry: MapEntry ->
            val materializedKey = materialize(entry.key, keyType)
            val materializedValue = materialize(entry.value, valueType)
            result[materializedKey] = materializedValue
        }

        return result
    }

    private fun buildObject(value: Value.Record, targetType: Class<*>): Any {
        buildKotlinObject(value, targetType)?.let { return it }
        buildWithNoArgConstructor(value, targetType)?.let { return it }
        buildWithSingleJavaConstructor(value, targetType)?.let { return it }
        throw ObjectConstructionException("No supported construction strategy found for ${targetType.name}")
    }

    private fun buildKotlinObject(value: Value.Record, targetType: Class<*>): Any? {
        val kClass: KClass<*> = targetType.kotlin
        val primary: KFunction<Any> = kClass.primaryConstructor ?: return null

        val valueParameters = primary.parameters.filter { it.kind == KParameter.Kind.VALUE }
        val parameterNames = valueParameters.mapNotNull { it.name }.toSet()

        val unknownFields = value.fields.keys - parameterNames
        if (unknownFields.isNotEmpty()) {
            return null
        }

        return try {
            val arguments = linkedMapOf<KParameter, Any?>()

            for (parameter in valueParameters) {
                val name = parameter.name
                    ?: throw ObjectConstructionException(
                        "Unnamed Kotlin constructor parameter on ${targetType.name}"
                    )

                val fieldValue = value.fields[name]
                if (fieldValue == null) {
                    if (parameter.isOptional) continue
                    if (parameter.type.isMarkedNullable) {
                        arguments[parameter] = null
                        continue
                    }
                    throw ObjectConstructionException(
                        "Missing constructor argument '$name' for ${targetType.name}"
                    )
                }

                arguments[parameter] = materialize(fieldValue, rawJavaTypeOf(parameter))
            }

            primary.callBy(arguments)
        } catch (e: ObjectConstructionException) {
            throw e
        } catch (e: Exception) {
            throw ObjectConstructionException("Failed to construct ${targetType.name}: ${e.message}", e)
        }
    }

    private fun buildWithNoArgConstructor(value: Value.Record, targetType: Class<*>): Any? {
        val ctor: Constructor<*> = targetType.declaredConstructors
            .firstOrNull { it.parameterCount == 0 }
            ?: return null

        return try {
            ctor.isAccessible = true
            val instance = ctor.newInstance()

            value.fields.forEach { (fieldName, fieldValue) ->
                val field = findField(targetType, fieldName)
                    ?: throw ObjectConstructionException(
                        "No field '$fieldName' found on ${targetType.name}"
                    )
                field.isAccessible = true
                field.set(instance, materialize(fieldValue, field.genericType))
            }

            instance
        } catch (e: ObjectConstructionException) {
            throw e
        } catch (e: Exception) {
            throw ObjectConstructionException("Failed to construct ${targetType.name}: ${e.message}", e)
        }
    }

    private fun buildWithSingleJavaConstructor(value: Value.Record, targetType: Class<*>): Any? {
        val constructors = targetType.declaredConstructors.toList()
        if (constructors.size != 1) {
            return null
        }

        val ctor = constructors[0]
        return try {
            val args = ArrayList<Any?>()

            ctor.parameters.forEachIndexed { index, parameter ->
                val fieldName =
                    if (parameter.isNamePresent && parameter.name != null && value.fields.containsKey(parameter.name)) {
                        parameter.name
                    } else {
                        "arg$index"
                    }

                val fieldValue = value.fields[fieldName]
                    ?: throw ObjectConstructionException(
                        "Missing constructor argument '$fieldName' for ${targetType.name}"
                    )

                args += materialize(fieldValue, parameter.parameterizedType)
            }

            ctor.isAccessible = true
            ctor.newInstance(*args.toTypedArray())
        } catch (e: ObjectConstructionException) {
            throw e
        } catch (e: Exception) {
            throw ObjectConstructionException("Failed to construct ${targetType.name}: ${e.message}", e)
        }
    }

    private fun findField(targetType: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = targetType
        while (current != null && current != Any::class.java) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun rawClassOf(type: Type): Class<*> =
        when (type) {
            is Class<*> -> type
            is ParameterizedType -> rawClassOf(type.rawType)
            else -> throw ObjectConstructionException("Unsupported target type: $type")
        }

    private fun rawJavaTypeOf(parameter: KParameter): Type =
        when (val classifier = parameter.type.classifier) {
            is KClass<*> -> classifier.java
            else -> throw ObjectConstructionException(
                "Unsupported Kotlin parameter type '${parameter.name}'"
            )
        }

    private fun wrapPrimitive(type: Class<*>): Class<*> =
        when (type) {
            Int::class.javaPrimitiveType -> Int::class.javaObjectType
            Long::class.javaPrimitiveType -> Long::class.javaObjectType
            Double::class.javaPrimitiveType -> Double::class.javaObjectType
            Float::class.javaPrimitiveType -> Float::class.javaObjectType
            Short::class.javaPrimitiveType -> Short::class.javaObjectType
            Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
            Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
            Char::class.javaPrimitiveType -> Char::class.javaObjectType
            Void.TYPE -> Void::class.java
            else -> type
        }
}

class SimpleScalarValueDecoder(
    private val predicate: (Any, Class<*>) -> Boolean,
    private val decoder: (Any, Class<*>) -> Any?
) : ScalarValueDecoder {
    override fun canDecode(rawValue: Any, targetType: Class<*>): Boolean =
        predicate(rawValue, targetType)

    override fun decode(rawValue: Any, targetType: Class<*>): Any? =
        decoder(rawValue, targetType)
}

object DefaultScalarValueDecoders {

    private fun decoder(
        targetType: Class<*>,
        decode: (String) -> Any?
    ): ScalarValueDecoder =
        SimpleScalarValueDecoder(
            predicate = { _, actualTargetType -> actualTargetType == targetType },
            decoder = { rawValue, _ -> decode(rawValue.toString()) }
        )

    private fun text(rawValue: Any): String =
        rawValue.toString()

    val all: List<ScalarValueDecoder> = listOf(
        decoder(String::class.java) { it },
        decoder(Int::class.javaObjectType) { it.toInt() },
        decoder(Long::class.javaObjectType) { it.toLong() },
        decoder(Double::class.javaObjectType) { it.toDouble() },
        decoder(Float::class.javaObjectType) { it.toFloat() },
        decoder(Short::class.javaObjectType) { it.toShort() },
        decoder(Byte::class.javaObjectType) { it.toByte() },
        decoder(Boolean::class.javaObjectType) {
            when (it.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("Invalid boolean value: $it")
            }
        },
        decoder(Char::class.javaObjectType) {
            require(it.length == 1) { "Invalid char value: $it" }
            it[0]
        },
        decoder(UUID::class.java) { UUID.fromString(it) },
        decoder(Instant::class.java) { Instant.parse(it) },
        decoder(LocalDate::class.java) { LocalDate.parse(it) },
        decoder(LocalDateTime::class.java) { LocalDateTime.parse(it) },
        decoder(LocalTime::class.java) { LocalTime.parse(it) },
        decoder(URI::class.java) { URI.create(it) },
        decoder(URL::class.java) { URI.create(it).toURL() },
        decoder(Path::class.java) { Paths.get(it) },
        decoder(Locale::class.java) { Locale.forLanguageTag(it) },
        decoder(Currency::class.java) { Currency.getInstance(it) },

        SimpleScalarValueDecoder(
            predicate = { _, targetType -> targetType.isEnum },
            decoder = { rawValue, targetType ->
                val text = text(rawValue)
                val constants = targetType.enumConstants
                    ?: throw IllegalArgumentException("No enum constants for ${targetType.name}")
                constants.firstOrNull { constant ->
                    val enumName = (constant as Enum<*>).name
                    enumName == text || enumName.equals(text, ignoreCase = true)
                } ?: throw IllegalArgumentException("Invalid enum value '$text' for ${targetType.name}")
            }
        )
    )
}
