package au.clef.engine.convert

import au.clef.engine.ObjectConstructionException
import au.clef.engine.TypeMismatchException
import au.clef.engine.model.Value
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class TypeConverter {

    fun materialize(value: Value, targetType: Class<*>): Any? {
        if (value is Value.Null) {
            if (targetType.isPrimitive) {
                throw TypeMismatchException(value, targetType)
            }
            return null
        }

        return when (value) {
            is Value.Primitive -> convertPrimitive(value.value, targetType)
            is Value.Object -> convertObject(value, targetType)
            is Value.Instance -> convertInstance(value, targetType)
            Value.Null -> null
        }
    }

    private fun convertPrimitive(rawValue: Any?, targetType: Class<*>): Any? {
        if (rawValue == null) {
            if (targetType.isPrimitive) {
                throw TypeMismatchException(Value.Primitive(null), targetType)
            }
            return null
        }

        if (targetType.isInstance(rawValue)) {
            return rawValue
        }

        val text: String = rawValue.toString()

        return when (targetType) {
            String::class.java -> text

            Int::class.javaObjectType,
            Int::class.javaPrimitiveType -> text.toInt()

            Long::class.javaObjectType,
            Long::class.javaPrimitiveType -> text.toLong()

            Double::class.javaObjectType,
            Double::class.javaPrimitiveType -> text.toDouble()

            Float::class.javaObjectType,
            Float::class.javaPrimitiveType -> text.toFloat()

            Short::class.javaObjectType,
            Short::class.javaPrimitiveType -> text.toShort()

            Byte::class.javaObjectType,
            Byte::class.javaPrimitiveType -> text.toByte()

            Boolean::class.javaObjectType,
            Boolean::class.javaPrimitiveType -> parseBoolean(text)

            Char::class.javaObjectType,
            Char::class.javaPrimitiveType -> parseChar(text)

            else -> {
                if (targetType.isEnum) {
                    convertEnum(text, targetType)
                } else {
                    throw TypeMismatchException(Value.Primitive(rawValue), targetType)
                }
            }
        }
    }

    private fun convertEnum(text: String, targetType: Class<*>): Any {
        val constants: Array<out Any> = targetType.enumConstants
            ?: throw TypeMismatchException(Value.Primitive(text), targetType)

        return constants.firstOrNull { constant: Any ->
            constant.toString() == text
        } ?: throw TypeMismatchException(Value.Primitive(text), targetType)
    }

    private fun parseBoolean(text: String): Boolean =
        when (text.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean value: $text")
        }

    private fun parseChar(text: String): Char {
        require(text.length == 1) { "Invalid char value: $text" }
        return text[0]
    }

    private fun convertInstance(value: Value.Instance, targetType: Class<*>): Any {
        val instance: Any = value.obj
        if (!targetType.isInstance(instance)) {
            throw TypeMismatchException(value, targetType)
        }
        return instance
    }

    private fun convertObject(value: Value.Object, targetType: Class<*>): Any {
        if (!targetType.isAssignableFrom(value.type)) {
            throw TypeMismatchException(value, targetType)
        }
        return buildObject(value, targetType)
    }

    private fun buildObject(value: Value.Object, targetType: Class<*>): Any {
        val kotlinBuilt: Any? = buildKotlinObject(value, targetType)
        if (kotlinBuilt != null) {
            return kotlinBuilt
        }

        val constructors: List<Constructor<*>> = targetType.declaredConstructors.toList()

        val noArgConstructor: Constructor<*>? =
            constructors.firstOrNull { constructor: Constructor<*> ->
                constructor.parameterCount == 0
            }

        if (noArgConstructor != null) {
            return buildWithNoArgConstructor(value, targetType, noArgConstructor)
        }

        val singleConstructor: Constructor<*>? =
            if (constructors.size == 1) constructors[0] else null

        if (singleConstructor != null) {
            return buildWithJavaConstructorArguments(value, targetType, singleConstructor)
        }

        throw ObjectConstructionException("No supported construction strategy found for ${targetType.name}")
    }

    private fun buildKotlinObject(value: Value.Object, targetType: Class<*>): Any? {
        val kClass: KClass<*> = targetType.kotlin
        val primary: KFunction<Any> = kClass.primaryConstructor ?: return null
        if (primary.parameters.isEmpty() && value.fields.isNotEmpty()) {
            return null
        }

        try {
            val arguments: Map<KParameter, Any?> =
                primary.parameters.associateWith { parameter: KParameter ->
                    val parameterName: String = parameter.name
                        ?: throw ObjectConstructionException("Unnamed Kotlin constructor parameter on ${targetType.name}")

                    val fieldValue: Value = value.fields[parameterName]
                        ?: throw ObjectConstructionException(
                            "Missing constructor argument '$parameterName' for ${targetType.name}"
                        )

                    val classifier: Any? = parameter.type.classifier
                    val parameterType: Class<*> =
                        if (classifier is KClass<*>) {
                            classifier.java
                        } else {
                            throw ObjectConstructionException(
                                "Unsupported Kotlin parameter type '$parameterName' on ${targetType.name}"
                            )
                        }

                    materialize(fieldValue, parameterType)
                }

            return primary.callBy(arguments)
        } catch (e: ObjectConstructionException) {
            throw e
        } catch (e: Exception) {
            throw ObjectConstructionException("Failed to construct ${targetType.name}: ${e.message}", e)
        }
    }

    private fun buildWithNoArgConstructor(
        value: Value.Object,
        targetType: Class<*>,
        constructor: Constructor<*>
    ): Any {
        try {
            constructor.isAccessible = true
            val instance: Any = constructor.newInstance()
            value.fields.forEach { (fieldName: String, fieldValue: Value) ->
                val field: Field = findField(targetType, fieldName)
                    ?: throw ObjectConstructionException("No field '$fieldName' found on ${targetType.name}")

                field.isAccessible = true
                val convertedValue: Any? = materialize(fieldValue, field.type)
                field.set(instance, convertedValue)
            }

            return instance
        } catch (e: ObjectConstructionException) {
            throw e
        } catch (e: Exception) {
            throw ObjectConstructionException("Failed to construct ${targetType.name}: ${e.message}", e)
        }
    }

    private fun buildWithJavaConstructorArguments(
        value: Value.Object,
        targetType: Class<*>,
        constructor: Constructor<*>
    ): Any {
        try {
            val parameterTypes: Array<Class<*>> = constructor.parameterTypes
            val arguments: Array<Any?> = Array(parameterTypes.size) { index: Int ->
                val fieldName = "arg$index"
                val fieldValue: Value = value.fields[fieldName]
                    ?: throw ObjectConstructionException("Missing constructor argument '$fieldName' for ${targetType.name}")

                materialize(fieldValue, parameterTypes[index])
            }

            constructor.isAccessible = true
            return constructor.newInstance(*arguments)
        } catch (e: ObjectConstructionException) {
            throw e
        } catch (e: Exception) {
            throw ObjectConstructionException("Failed to construct ${targetType.name}: ${e.message}", e)
        }
    }

    private fun findField(targetType: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = targetType
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }

        return null
    }
}