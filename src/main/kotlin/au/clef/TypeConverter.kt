package au.clef

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class TypeConverter {

    fun materialize(value: Any?, targetType: Class<*>): Any? =
        tryConvert(value, targetType)?.value ?: throw TypeMismatchException(value, targetType)

    private fun normalize(type: Class<*>): Class<*> = when (type) {
        Integer.TYPE -> Int::class.java
        java.lang.Long.TYPE -> Long::class.java
        java.lang.Double.TYPE -> Double::class.java
        java.lang.Float.TYPE -> Float::class.java
        java.lang.Boolean.TYPE -> Boolean::class.java
        java.lang.Short.TYPE -> Short::class.java
        java.lang.Byte.TYPE -> Byte::class.java
        Character.TYPE -> Char::class.java
        else -> type
    }

    private fun tryConvert(value: Any?, targetType: Class<*>): ConversionResult? {
        val normalizedTarget: Class<*> = normalize(targetType)
        val unwrapped: Any? = when (value) {
            is Value.Primitive -> value.value
            is Value.Instance -> value.obj
            is Value.Object -> {
                val built: Any = buildObject(value)
                if (normalizedTarget.isAssignableFrom(built.javaClass)) {
                    return ConversionResult(built)
                }
                return null
            }
            else -> value
        }

        if (unwrapped == null) {
            return if (targetType.isPrimitive) null else ConversionResult(null)
        }

        return when (normalizedTarget) {
            Int::class.java -> when (unwrapped) {
                is Int -> ConversionResult(unwrapped)
                is String -> unwrapped.toIntOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            Long::class.java -> when (unwrapped) {
                is Long -> ConversionResult(unwrapped)
                is Int -> ConversionResult(unwrapped.toLong())
                is String -> unwrapped.toLongOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            Double::class.java -> when (unwrapped) {
                is Double -> ConversionResult(unwrapped)
                is Int -> ConversionResult(unwrapped.toDouble())
                is Long -> ConversionResult(unwrapped.toDouble())
                is String -> unwrapped.toDoubleOrNull()?.let { ConversionResult(it) }
                else -> null
            }
            String::class.java -> when (unwrapped) {
                is String -> ConversionResult(unwrapped)
                else -> ConversionResult(unwrapped.toString())
            }
            Boolean::class.java -> when (unwrapped) {
                is Boolean -> ConversionResult(unwrapped)
                is String -> when (unwrapped.lowercase()) {
                    "true" -> ConversionResult(true)
                    "false" -> ConversionResult(false)
                    else -> null
                }
                else -> null
            }
            else -> {
                if (normalizedTarget.isAssignableFrom(unwrapped.javaClass)) {
                    ConversionResult(unwrapped)
                } else {
                    null
                }
            }
        }
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

    private fun buildKotlinObject(obj: Value.Object): Any {
        val kClass: KClass<out Any> = obj.type.kotlin
        val ctor: KFunction<Any> = kClass.primaryConstructor
            ?: throw ObjectConstructionException(obj.type, "No primary constructor")

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
                (param.type.classifier as? KClass<*>)?.java
                    ?: throw ObjectConstructionException(obj.type, "Unsupported type for '$name'")

            argsByParam[param] = materialize(rawValue, paramClass)
        }

        return try {
            ctor.callBy(argsByParam)
        } catch (e: Exception) {
            throw ObjectConstructionException(obj.type, "Constructor invocation failed", e)
        }
    }

    private fun buildJavaObject(obj: Value.Object): Any {
        val clazz: Class<*> = obj.type
        var lastFailure: Exception? = null

        for (ctor: Constructor<*> in clazz.declaredConstructors) {
            val params = ctor.parameters
            if (params.size != obj.fields.size) continue

            try {
                val args: Array<Any?> = params.mapIndexed { i: Int, param ->
                    val value: Value =
                        obj.fields[param.name]
                            ?: obj.fields["arg$i"]
                            ?: obj.fields.values.elementAtOrNull(i)
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

    private fun isKotlinClass(clazz: Class<*>): Boolean =
        clazz.getAnnotation(Metadata::class.java) != null
}