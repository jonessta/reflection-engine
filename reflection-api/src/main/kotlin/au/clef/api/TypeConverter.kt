package au.clef.api

import au.clef.api.model.Value
import au.clef.engine.ObjectConstructionException
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Array
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType

class TypeConverter(
    private val scalarRegistry: ScalarTypeRegistry
) {

    fun materialize(value: Value, targetType: Class<*>): Any? =
        materializeInternal(value, targetType)

    fun materialize(value: Value, targetType: Type): Any? =
        materializeInternal(value, targetType)

    fun supportsScalarTarget(targetType: Class<*>): Boolean =
        scalarRegistry.isScalarLike(targetType)

    private fun materializeInternal(value: Value, target: Type): Any? {
        val rawTarget: Class<*> = rawClassOf(target)

        if (value is Value.Null) {
            return handleNull(rawTarget)
        }

        return when (value) {
            is Value.Scalar ->
                convertScalar(value.value, target)

            is Value.Record ->
                buildObject(value, rawTarget)

            is Value.ListValue ->
                convertList(value, target)

            is Value.MapValue ->
                convertMap(value, target)

            is Value.Instance ->
                convertInstance(value, target)

            Value.Null ->
                error("Value.Null handled earlier")
        }
    }

    private fun convertScalar(rawValue: Any?, target: Type): Any? {
        val rawTarget: Class<*> = rawClassOf(target)
        val wrappedTarget: Class<*> = scalarRegistry.wrapPrimitive(rawTarget)
        val normalized: Any = normalizeScalar(rawValue) ?: return handleNull(rawTarget)

        if (wrappedTarget.isInstance(normalized)) {
            return normalized
        }

        if (wrappedTarget.isEnum) {
            return decodeEnum(normalized.toString(), wrappedTarget)
        }

        val decoder: ScalarConverter<Any> =
            scalarRegistry.decoderFor(wrappedTarget)
                ?: throw TypeMismatchException(Value.Scalar(rawValue), rawTarget)

        return try {
            decoder.decode(normalized.toString())
        } catch (e: Exception) {
            throw TypeMismatchException(Value.Scalar(rawValue), rawTarget)
        }
    }

    private fun convertList(value: Value.ListValue, target: Type): Any {
        val rawTarget: Class<*> = rawClassOf(target)
        val elementType: Type =
            (target as? ParameterizedType)?.actualTypeArguments?.getOrNull(0)
                ?: Any::class.java

        val items: List<Any?> =
            value.items.map { item: Value ->
                materializeInternal(item, elementType)
            }

        return when {
            rawTarget.isArray -> {
                val componentType: Class<*> = rawTarget.componentType
                val array: Any = Array.newInstance(componentType, items.size)
                items.forEachIndexed { index: Int, item: Any? ->
                    Array.set(array, index, item)
                }
                array
            }

            Set::class.java.isAssignableFrom(rawTarget) -> {
                items.toSet()
            }

            List::class.java.isAssignableFrom(rawTarget) ||
                    Collection::class.java.isAssignableFrom(rawTarget) -> {
                items.toMutableList()
            }

            else -> {
                throw TypeMismatchException(value, rawTarget)
            }
        }
    }

    private fun convertMap(value: Value.MapValue, target: Type): Any {
        val rawTarget: Class<*> = rawClassOf(target)

        if (!Map::class.java.isAssignableFrom(rawTarget)) {
            throw TypeMismatchException(value, rawTarget)
        }

        val keyType: Type =
            (target as? ParameterizedType)?.actualTypeArguments?.getOrNull(0)
                ?: Any::class.java

        val valueType: Type =
            (target as? ParameterizedType)?.actualTypeArguments?.getOrNull(1)
                ?: Any::class.java

        return value.entries
            .associate { entry ->
                val key: Any? = materializeInternal(entry.key, keyType)
                val mappedValue: Any? = materializeInternal(entry.value, valueType)
                key to mappedValue
            }
            .toMutableMap()
    }

    private fun convertInstance(value: Value.Instance, target: Type): Any {
        val rawTarget: Class<*> = scalarRegistry.wrapPrimitive(rawClassOf(target))

        if (!rawTarget.isInstance(value.obj)) {
            throw TypeMismatchException(value, rawTarget)
        }

        return value.obj
    }

    private fun buildObject(value: Value.Record, target: Class<*>): Any {
        return tryBuildKotlinObject(value, target)
            ?: tryBuildWithSingleJavaConstructor(value, target)
            ?: tryBuildWithNoArgAndFields(value, target)
            ?: throw ObjectConstructionException(
                "No construction strategy for ${target.name}"
            )
    }

    private fun tryBuildKotlinObject(value: Value.Record, target: Class<*>): Any? {
        val kClass: KClass<*> = target.kotlin
        val primaryConstructor: KFunction<Any>? =
            kClass.primaryConstructor

        if (primaryConstructor == null) {
            return null
        }

        val constructorParameterNames: Set<String> =
            primaryConstructor.parameters.mapNotNull { parameter: KParameter ->
                parameter.name
            }.toSet()

        if ((value.fields.keys - constructorParameterNames).isNotEmpty()) {
            return null
        }

        val arguments: Map<KParameter, Any?> =
            primaryConstructor.parameters.associateWith { parameter: KParameter ->
                val parameterName: String =
                    parameter.name
                        ?: throw ObjectConstructionException(
                            "Unnamed Kotlin constructor parameter on ${target.name}"
                        )

                val fieldValue: Value? = value.fields[parameterName]

                when {
                    fieldValue != null -> {
                        materializeInternal(fieldValue, parameter.type.javaType)
                    }

                    parameter.isOptional -> {
                        null
                    }

                    parameter.type.isMarkedNullable -> {
                        null
                    }

                    else -> {
                        throw ObjectConstructionException(
                            "Missing mandatory parameter '$parameterName' for ${target.name}"
                        )
                    }
                }
            }.filterNot { entry: Map.Entry<KParameter, Any?> ->
                entry.key.isOptional && value.fields[entry.key.name] == null
            }

        return try {
            primaryConstructor.callBy(arguments)
        } catch (e: ObjectConstructionException) {
            throw e
        } catch (e: Exception) {
            throw ObjectConstructionException(
                "Failed to construct ${target.name} with Kotlin primary constructor: ${e.message}",
                e
            )
        }
    }

    private fun tryBuildWithSingleJavaConstructor(
        value: Value.Record,
        target: Class<*>
    ): Any? {
        val constructors = target.declaredConstructors
        if (constructors.size != 1) {
            return null
        }

        val constructor: Constructor<*> = constructors[0]
        if (constructor.parameterCount == 0) {
            return null
        }

        val arguments: List<Any?> =
            constructor.parameters.mapIndexed { index: Int, parameter: Parameter ->
                val parameterName: String =
                    if (parameter.isNamePresent) {
                        parameter.name
                    } else {
                        "arg$index"
                    }

                val fieldValue: Value =
                    value.fields[parameterName]
                        ?: throw ObjectConstructionException(
                            "Missing constructor argument '$parameterName' for ${target.name}"
                        )

                materializeInternal(fieldValue, parameter.parameterizedType)
            }

        return try {
            constructor.isAccessible = true
            constructor.newInstance(*arguments.toTypedArray())
        } catch (e: ObjectConstructionException) {
            throw e
        } catch (e: Exception) {
            throw ObjectConstructionException(
                "Failed to construct ${target.name} with Java constructor: ${e.message}",
                e
            )
        }
    }

    private fun tryBuildWithNoArgAndFields(
        value: Value.Record,
        target: Class<*>
    ): Any? {
        val constructor: Constructor<*> =
            target.declaredConstructors.firstOrNull { ctor: Constructor<*> ->
                ctor.parameterCount == 0
            } ?: return null

        val instance: Any =
            try {
                constructor.isAccessible = true
                constructor.newInstance()
            } catch (e: Exception) {
                throw ObjectConstructionException(
                    "Failed to instantiate ${target.name} with no-arg constructor: ${e.message}",
                    e
                )
            }

        value.fields.forEach { (fieldName: String, fieldValue: Value) ->
            val field: Field =
                findField(target, fieldName)
                    ?: throw ObjectConstructionException(
                        "Field '$fieldName' not found on ${target.name}"
                    )

            try {
                field.isAccessible = true
                val convertedValue: Any? =
                    materializeInternal(fieldValue, field.genericType)
                field.set(instance, convertedValue)
            } catch (e: ObjectConstructionException) {
                throw e
            } catch (e: Exception) {
                throw ObjectConstructionException(
                    "Failed to set field '$fieldName' on ${target.name}: ${e.message}",
                    e
                )
            }
        }

        return instance
    }

    private fun normalizeScalar(rawValue: Any?): Any? =
        when (rawValue) {
            is JsonPrimitive -> {
                if (rawValue.isString) {
                    rawValue.content
                } else {
                    val text: String = rawValue.content
                    text.toLongOrNull()
                        ?: text.toDoubleOrNull()
                        ?: text.toBooleanStrictOrNull()
                        ?: text
                }
            }

            else -> rawValue
        }

    private fun decodeEnum(text: String, target: Class<*>): Any {
        return target.enumConstants.firstOrNull { constant: Any ->
            (constant as Enum<*>).name.equals(text, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "Invalid enum '$text' for ${target.name}"
        )
    }

    private fun findField(target: Class<*>, name: String): Field? {
        return generateSequence(target) { current: Class<*> ->
            current.superclass
        }.takeWhile { current: Class<*> ->
            current != Any::class.java
        }.mapNotNull { current: Class<*> ->
            runCatching { current.getDeclaredField(name) }.getOrNull()
        }.firstOrNull()
    }

    private fun handleNull(rawTarget: Class<*>): Any? {
        if (rawTarget.isPrimitive) {
            throw TypeMismatchException(Value.Null, rawTarget)
        }
        return null
    }

    private fun rawClassOf(type: Type): Class<*> =
        when (type) {
            is Class<*> -> type
            is ParameterizedType -> rawClassOf(type.rawType)
            else -> throw IllegalArgumentException("Unsupported Type: $type")
        }
}
