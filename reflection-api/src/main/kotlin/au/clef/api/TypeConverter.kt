package au.clef.api

import au.clef.api.model.ScalarValue
import au.clef.api.model.Value
import au.clef.engine.ObjectConstructionException
import au.clef.engine.TypeReflection
import au.clef.engine.TypeReflection.findField
import java.lang.reflect.*
import java.lang.reflect.Array.newInstance
import java.lang.reflect.Array.set
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType
class TypeConverter(private val scalarRegistry: ScalarTypeRegistry) {

    fun materialize(value: Value, targetType: Class<*>): Any? =
        materializeInternal(value, targetType)

    fun materialize(value: Value, targetType: Type): Any? =
        materializeInternal(value, targetType)

    fun supportsScalarTarget(targetType: Class<*>): Boolean =
        scalarRegistry.isScalarLike(targetType)

    private fun materializeInternal(value: Value, target: Type): Any? {
        val rawTarget: Class<*> = TypeReflection.rawClassOf(target)
        if (value is Value.Null) {
            return handleNull(rawTarget)
        }

        return when (value) {
            is Value.Scalar -> convertScalar(value.value, target)
            is Value.Record -> buildObject(value, rawTarget)
            is Value.ListValue -> convertList(value, target)
            is Value.MapValue -> convertMap(value, target)
            Value.Null -> error("Value.Null handled earlier")
        }
    }

    private fun convertScalar(value: ScalarValue, target: Type): Any? {
        val rawTarget: Class<*> = TypeReflection.rawClassOf(target)
        val wrappedTarget: Class<*> = scalarRegistry.wrapPrimitive(rawTarget)

        if (wrappedTarget == Any::class.java || wrappedTarget == Object::class.java) {
            return when (value) {
                is ScalarValue.StringValue -> value.value
                is ScalarValue.BooleanValue -> value.value
                is ScalarValue.NumberValue -> {
                    value.value.toLongOrNull() ?: value.value.toDoubleOrNull() ?: value.value
                }
            }
        }

        if (wrappedTarget.isEnum) {
            val text: String =
                when (value) {
                    is ScalarValue.StringValue -> value.value
                    else -> throw TypeMismatchException(Value.Scalar(value), rawTarget)
                }

            return decodeEnum(text, wrappedTarget)
        }

        if (wrappedTarget == String::class.java) {
            return when (value) {
                is ScalarValue.StringValue -> value.value
                else -> throw TypeMismatchException(Value.Scalar(value), rawTarget)
            }
        }
        val decoder: ScalarConverter<Any> = scalarRegistry.decoderFor(wrappedTarget)
            ?: throw TypeMismatchException(Value.Scalar(value), rawTarget)

        return try {
            decoder.decode(value)
        } catch (e: Exception) {
            throw TypeMismatchException(Value.Scalar(value), rawTarget)
        }
    }

    private fun convertList(value: Value.ListValue, target: Type): Any {
        val rawTarget: Class<*> = TypeReflection.rawClassOf(target)
        val elementType: Type =
            when {
                rawTarget.isArray -> rawTarget.componentType
                target is ParameterizedType -> target.actualTypeArguments.getOrNull(0)
                    ?: Any::class.java

                else -> Any::class.java
            }
        val items: List<Any?> =
            value.items.map { item: Value -> materializeInternal(item, elementType) }

        return when {
            rawTarget.isArray -> {
                val componentType: Class<*> = rawTarget.componentType
                val array: Any = newInstance(componentType, items.size)
                for ((index: Int, item: Any?) in items.withIndex()) {
                    set(array, index, item)
                }
                array
            }

            Set::class.java.isAssignableFrom(rawTarget) -> items.toSet()
            List::class.java.isAssignableFrom(rawTarget) ||
                    Collection::class.java.isAssignableFrom(rawTarget) -> items.toMutableList()

            else -> throw TypeMismatchException(value, rawTarget)
        }
    }

    private fun convertMap(value: Value.MapValue, target: Type): Any {
        val rawTarget: Class<*> = TypeReflection.rawClassOf(target)

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

    private fun buildObject(value: Value.Record, target: Class<*>): Any =
        tryBuildKotlinObject(value, target)
            ?: tryBuildWithSingleJavaConstructor(value, target)
            ?: tryBuildWithNoArgAndFields(value, target)
            ?: throw ObjectConstructionException("No construction strategy for ${target.name}")

    private fun tryBuildKotlinObject(value: Value.Record, target: Class<*>): Any? {
        val kClass: KClass<*> = target.kotlin
        val primaryConstructor: KFunction<Any> = kClass.primaryConstructor ?: return null

        val valueParameters: List<KParameter> = primaryConstructor.parameters
            .filter { parameter: KParameter -> parameter.kind == KParameter.Kind.VALUE }

        val constructorParameterNames: Set<String> = valueParameters
            .mapNotNull { parameter: KParameter -> parameter.name }
            .toSet()

        if ((value.fields.keys - constructorParameterNames).isNotEmpty()) {
            return null
        }

        val hasValueClassParameters: Boolean = valueParameters
            .any { parameter: KParameter ->
                val classifier = parameter.type.classifier as? KClass<*>
                classifier?.isValue == true
            }

        val missingOptional = mutableSetOf<KParameter>()
        val orderedArgs = mutableListOf<Any?>()
        val callByArgs = linkedMapOf<KParameter, Any?>()

        for (parameter in valueParameters) {
            val parameterName: String =
                parameter.name ?: throw ObjectConstructionException(
                    "Unnamed Kotlin constructor parameter on ${target.name}"
                )

            val fieldValue: Value? = value.fields[parameterName]

            when {
                fieldValue != null -> {
                    val converted = materializeInternal(fieldValue, parameter.type.javaType)
                    orderedArgs += converted
                    callByArgs[parameter] = converted
                }

                parameter.isOptional -> missingOptional += parameter

                parameter.type.isMarkedNullable -> {
                    orderedArgs += null
                    callByArgs[parameter] = null
                }

                else -> {
                    throw ObjectConstructionException(
                        "Missing mandatory parameter '$parameterName' for ${target.name}"
                    )
                }
            }
        }

        if (!hasValueClassParameters) {
            return try {
                if (missingOptional.isEmpty()) {
                    primaryConstructor.call(*orderedArgs.toTypedArray())
                } else {
                    primaryConstructor.callBy(callByArgs)
                }
            } catch (e: ObjectConstructionException) {
                throw e
            } catch (e: Exception) {
                throw ObjectConstructionException(
                    "Failed to construct ${target.name} with Kotlin primary constructor: ${e.message}",
                    e
                )
            }
        }

        val candidateConstructors: List<Constructor<*>> = target.declaredConstructors
            .filter { constructor: Constructor<*> ->
                !constructor.isSynthetic && constructor.parameterCount == orderedArgs.size
            }

        val matchingConstructor: Constructor<*>? = candidateConstructors
            .firstOrNull { constructor: Constructor<*> ->
                constructor.parameterTypes.indices.all { index: Int ->
                    val expected: Class<*> =
                        scalarRegistry.wrapPrimitive(constructor.parameterTypes[index])
                    val actual: Any? = orderedArgs[index]
                    actual == null || expected.isInstance(actual)
                }
            }

        if (matchingConstructor != null && missingOptional.isEmpty()) {
            return try {
                matchingConstructor.isAccessible = true
                matchingConstructor.newInstance(*orderedArgs.toTypedArray())
            } catch (e: ObjectConstructionException) {
                throw e
            } catch (e: Exception) {
                throw ObjectConstructionException(
                    "Failed to construct ${target.name} with Java-backed Kotlin constructor: ${e.message}",
                    e
                )
            }
        }

        return null
    }

    private fun tryBuildWithSingleJavaConstructor(value: Value.Record, target: Class<*>): Any? {
        val constructor: Constructor<*> = target.declaredConstructors.singleOrNull() ?: return null
        if (constructor.parameterCount == 0) {
            return null
        }
        val arguments: List<Any?> = constructor.parameters
            .mapIndexed { index: Int, parameter: Parameter ->
                val parameterName: String =
                    if (parameter.isNamePresent) parameter.name else "arg$index"
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
                "Failed to construct ${target.name} with Java constructor: ${e.message}", e
            )
        }
    }

    private fun tryBuildWithNoArgAndFields(value: Value.Record, target: Class<*>): Any? {
        val constructor: Constructor<*> = target.declaredConstructors
            .firstOrNull { ctor: Constructor<*> -> ctor.parameterCount == 0 } ?: return null

        val instance: Any =
            try {
                constructor.isAccessible = true
                constructor.newInstance()
            } catch (e: Exception) {
                throw ObjectConstructionException(
                    "Failed to instantiate ${target.name} with no-arg constructor: ${e.message}", e
                )
            }

        value.fields.forEach { (fieldName: String, fieldValue: Value) ->
            val field: Field = findField(target, fieldName)
                ?: throw ObjectConstructionException("Field '$fieldName' not found on ${target.name}")

            try {
                field.isAccessible = true
                val convertedValue: Any? = materializeInternal(fieldValue, field.genericType)
                field.set(instance, convertedValue)
            } catch (e: ObjectConstructionException) {
                throw e
            } catch (e: Exception) {
                throw ObjectConstructionException(
                    "Failed to set field '$fieldName' on ${target.name}: ${e.message}", e
                )
            }
        }

        return instance
    }

    private fun decodeEnum(text: String, target: Class<*>): Any =
        target.enumConstants.firstOrNull { constant: Any ->
            (constant as Enum<*>).name.equals(text, ignoreCase = true)
        } ?: throw IllegalArgumentException("Invalid enum '$text' for ${target.name}")


    private fun handleNull(rawTarget: Class<*>): Any? {
        if (rawTarget.isPrimitive) {
            throw TypeMismatchException(Value.Null, rawTarget)
        }
        return null
    }
}