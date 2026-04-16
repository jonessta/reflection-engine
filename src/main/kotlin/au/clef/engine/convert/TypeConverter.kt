package au.clef.engine.convert

import au.clef.ObjectConstructionException
import au.clef.TypeMismatchException
import au.clef.engine.model.Value
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class TypeConverter {
    fun materialize(value: Any?, targetType: Class<*>): Any? {
        val result: ConversionResult? = tryConvert(value, targetType)
        return result?.value ?: throw TypeMismatchException(value, targetType)
    }

    private fun tryConvert(value: Any?, targetType: Class<*>): ConversionResult? {
        val unwrapped: Any? = when (value) {
            is Value.Primitive -> value.value
            is Value.Instance -> value.obj
            is Value.Object -> {
                val built: Any = buildObject(value)
                if (targetType.isAssignableFrom(built.javaClass) || isBoxedOrPrimitiveMatch(
                        targetType, built.javaClass
                    )
                ) {
                    return ConversionResult(built)
                }
                return null
            }
            else -> value
        }

        if (unwrapped == null) {
            return if (targetType.isPrimitive) null else ConversionResult(null)
        }

        return when {
            isIntType(targetType) -> when (unwrapped) {
                is Int -> ConversionResult(unwrapped)
                is String -> unwrapped.toIntOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            isLongType(targetType) -> when (unwrapped) {
                is Long -> ConversionResult(unwrapped)
                is Int -> ConversionResult(unwrapped.toLong())
                is String -> unwrapped.toLongOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            isDoubleType(targetType) -> when (unwrapped) {
                is Double -> ConversionResult(unwrapped)
                is Int -> ConversionResult(unwrapped.toDouble())
                is Long -> ConversionResult(unwrapped.toDouble())
                is String -> unwrapped.toDoubleOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            isFloatType(targetType) -> when (unwrapped) {
                is Float -> ConversionResult(unwrapped)
                is Int -> ConversionResult(unwrapped.toFloat())
                is Long -> ConversionResult(unwrapped.toFloat())
                is Double -> ConversionResult(unwrapped.toFloat())
                is String -> unwrapped.toFloatOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            isBooleanType(targetType) -> when (unwrapped) {
                is Boolean -> ConversionResult(unwrapped)
                is String -> when (unwrapped.lowercase()) {
                    "true" -> ConversionResult(true)
                    "false" -> ConversionResult(false)
                    else -> null
                }
                else -> null
            }
            isShortType(targetType) -> when (unwrapped) {
                is Short -> ConversionResult(unwrapped)
                is Int -> ConversionResult(unwrapped.toShort())
                is String -> unwrapped.toShortOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            isByteType(targetType) -> when (unwrapped) {
                is Byte -> ConversionResult(unwrapped)
                is Int -> ConversionResult(unwrapped.toByte())
                is String -> unwrapped.toByteOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            isCharType(targetType) -> when (unwrapped) {
                is Char -> ConversionResult(unwrapped)
                is String -> unwrapped.singleOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            isStringType(targetType) -> when (unwrapped) {
                is String -> ConversionResult(unwrapped)
                else -> ConversionResult(unwrapped.toString())
            }
            targetType.isAssignableFrom(unwrapped.javaClass) -> ConversionResult(unwrapped)
            else -> null
        }
    }

    private fun isIntType(type: Class<*>): Boolean =
        type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType

    private fun isLongType(type: Class<*>): Boolean =
        type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType

    private fun isDoubleType(type: Class<*>): Boolean =
        type == Double::class.javaPrimitiveType || type == Double::class.javaObjectType

    private fun isFloatType(type: Class<*>): Boolean =
        type == Float::class.javaPrimitiveType || type == Float::class.javaObjectType

    private fun isBooleanType(type: Class<*>): Boolean =
        type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType

    private fun isShortType(type: Class<*>): Boolean =
        type == Short::class.javaPrimitiveType || type == Short::class.javaObjectType

    private fun isByteType(type: Class<*>): Boolean =
        type == Byte::class.javaPrimitiveType || type == Byte::class.javaObjectType

    private fun isCharType(type: Class<*>): Boolean =
        type == Char::class.javaPrimitiveType || type == Char::class.javaObjectType

    private fun isStringType(type: Class<*>): Boolean = type == String::class.java

    private fun isBoxedOrPrimitiveMatch(targetType: Class<*>, actualType: Class<*>): Boolean = when {
        isIntType(targetType) -> isIntType(actualType)
        isLongType(targetType) -> isLongType(actualType)
        isDoubleType(targetType) -> isDoubleType(actualType)
        isFloatType(targetType) -> isFloatType(actualType)
        isBooleanType(targetType) -> isBooleanType(actualType)
        isShortType(targetType) -> isShortType(actualType)
        isByteType(targetType) -> isByteType(actualType)
        isCharType(targetType) -> isCharType(actualType)
        else -> false
    }

    private data class ConversionResult(val value: Any?)

    private fun buildObject(obj: Value.Object): Any {
        val clazz: Class<*> = obj.type
        return if (isKotlinClass(clazz)) {
            buildKotlinObject(obj)
        } else {
            buildJavaObject(obj)
        }
    }

    // Kotlin reflection
    private fun buildKotlinObject(obj: Value.Object): Any {
        val kClass: KClass<out Any> = obj.type.kotlin
        val ctor: KFunction<Any> =
            kClass.primaryConstructor ?: throw ObjectConstructionException(obj.type, "No primary constructor")

        val argsByParam: MutableMap<KParameter, Any?> = mutableMapOf()

        for (param: KParameter in ctor.parameters) {
            val name: String =
                param.name ?: throw ObjectConstructionException(obj.type, "Unnamed constructor parameter")

            val rawValue: Value? = obj.fields[name]
            if (rawValue == null) {
                if (param.isOptional) continue
                if (param.type.isMarkedNullable) {
                    argsByParam[param] = null
                    continue
                }
                throw ObjectConstructionException(obj.type, "Missing field '$name'")
            }

            val paramClass: Class<out Any> =
                (param.type.classifier as? KClass<*>)?.java ?: throw ObjectConstructionException(
                    obj.type, "Unsupported type for '$name'"
                )

            argsByParam[param] = materialize(rawValue, paramClass)
        }

        return try {
            ctor.callBy(argsByParam)
        } catch (e: Exception) {
            throw ObjectConstructionException(obj.type, "Constructor invocation failed", e)
        }
    }

    // Java reflection fallback
    private fun buildJavaObject(obj: Value.Object): Any {
        val clazz: Class<*> = obj.type
        var lastFailure: Exception? = null

        for (ctor: Constructor<*> in clazz.declaredConstructors) {
            val params = ctor.parameters
            if (params.size != obj.fields.size) continue

            try {
                val args: Array<Any?> = params.mapIndexed { i: Int, param ->
                    val value: Value =
                        obj.fields[param.name] ?: obj.fields["arg$i"] ?: obj.fields.values.elementAtOrNull(i)
                        ?: throw ObjectConstructionException(clazz, "Missing value for param $i")

                    materialize(value, param.type)
                }.toTypedArray()

                ctor.isAccessible = true
                return ctor.newInstance(*args)
            } catch (e: Exception) {
                lastFailure = e
            }
        }

        val noArgCtor: Constructor<*> = clazz.declaredConstructors.firstOrNull { it.parameterCount == 0 }
            ?: throw ObjectConstructionException(clazz, "No suitable constructor", lastFailure)

        return try {
            noArgCtor.isAccessible = true
            val instance: Any = noArgCtor.newInstance()

            obj.fields.forEach { (name: String, value: Value) ->
                val field: Field = clazz.getDeclaredField(name)
                field.isAccessible = true
                field.set(instance, materialize(value, field.type))
            }

            instance
        } catch (e: Exception) {
            throw ObjectConstructionException(clazz, "No-arg construction failed", e)
        }
    }

    private fun isKotlinClass(clazz: Class<*>): Boolean = clazz.getAnnotation(Metadata::class.java) != null
}