package au.clef.engine.convert

import au.clef.engine.ObjectConstructionException
import au.clef.engine.TypeMismatchException
import au.clef.engine.model.Value
import java.lang.reflect.*
import java.lang.reflect.Array
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class TypeConverter {

    fun materialize(value: Value, targetType: Class<*>): Any? =
        materialize(value, targetType as Type)

    fun materialize(value: Value, targetType: Type): Any? {
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
        if (wrappedTargetType.isInstance(rawValue)) {
            return rawValue
        }

        val text = rawValue.toString()
        try {
            return when (wrappedTargetType) {
                String::class.java -> text
                Int::class.javaObjectType -> text.toInt()
                Long::class.javaObjectType -> text.toLong()
                Double::class.javaObjectType -> text.toDouble()
                Float::class.javaObjectType -> text.toFloat()
                Short::class.javaObjectType -> text.toShort()
                Byte::class.javaObjectType -> text.toByte()
                Boolean::class.javaObjectType -> parseBoolean(text)
                Char::class.javaObjectType -> parseChar(text)
                UUID::class.java -> UUID.fromString(text)
                Instant::class.java -> Instant.parse(text)
                LocalDate::class.java -> LocalDate.parse(text)
                LocalDateTime::class.java -> LocalDateTime.parse(text)
                LocalTime::class.java -> LocalTime.parse(text)
                else -> {
                    if (wrappedTargetType.isEnum) {
                        convertEnum(text, wrappedTargetType)
                    } else {
                        throw TypeMismatchException(Value.Scalar(rawValue), rawTargetType)
                    }
                }
            }
        } catch (e: TypeMismatchException) {
            throw e
        } catch (e: Exception) {
            throw TypeMismatchException(Value.Scalar(rawValue), rawTargetType)
        }
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
                materializedItems.forEachIndexed { index, element -> Array.set(array, index, element) }
                array
            }

            Set::class.java.isAssignableFrom(rawTargetType) -> {
                LinkedHashSet(materializedItems)
            }

            List::class.java.isAssignableFrom(rawTargetType) ||
                    Collection::class.java.isAssignableFrom(rawTargetType) -> {
                materializedItems.toMutableList()
            }

            Iterable::class.java.isAssignableFrom(rawTargetType) -> {
                materializedItems
            }

            else -> throw TypeMismatchException(value, rawTargetType)
        }
    }

    private fun convertMap(value: Value.MapValue, targetType: Type): Any {
        val rawTargetType = rawClassOf(targetType)
        if (!Map::class.java.isAssignableFrom(rawTargetType)) {
            throw TypeMismatchException(value, rawTargetType)
        }

        val keyType = when (targetType) {
            is ParameterizedType -> targetType.actualTypeArguments[0]
            else -> String::class.java
        }

        val valueType = when (targetType) {
            is ParameterizedType -> targetType.actualTypeArguments[1]
            else -> Any::class.java
        }

        val rawKeyType = rawClassOf(keyType)
        if (rawKeyType != String::class.java) {
            throw ObjectConstructionException("MapValue only supports String keys, but target type requires ${rawKeyType.name}")
        }

        val result = LinkedHashMap<String, Any?>()
        value.entries.forEach { (key, entryValue) ->
            result[key] = materialize(entryValue, valueType)
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
                    if (parameter.isOptional) {
                        continue
                    }
                    if (parameter.type.isMarkedNullable) {
                        arguments[parameter] = null
                        continue
                    }
                    throw ObjectConstructionException("Missing constructor argument '$name' for ${targetType.name}")
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
        val constructors: List<Constructor<*>> = targetType.declaredConstructors.toList()
        if (constructors.size != 1) {
            return null
        }

        val ctor: Constructor<*> = constructors[0]
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
                    ?: throw ObjectConstructionException("Missing constructor argument '$fieldName' for ${targetType.name}")

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

    private fun convertEnum(text: String, targetType: Class<*>): Any {
        val constants: kotlin.Array<out Any?> = targetType.enumConstants
            ?: throw TypeMismatchException(Value.Scalar(text), targetType)

        return constants.firstOrNull { constant ->
            val enumName = (constant as Enum<*>).name
            enumName == text || enumName.equals(text, ignoreCase = true)
        } ?: throw TypeMismatchException(Value.Scalar(text), targetType)
    }

    private fun parseBoolean(text: String): Boolean =
        when (text.trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean value: $text")
        }

    private fun parseChar(text: String): Char {
        require(text.length == 1) { "Invalid char value: $text" }
        return text[0]
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

    private fun rawJavaTypeOf(parameter: KParameter): Type {
        return when (val classifier = parameter.type.classifier) {
            is KClass<*> -> classifier.java
            else -> throw ObjectConstructionException(
                "Unsupported Kotlin parameter type '${parameter.name}'"
            )
        }
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