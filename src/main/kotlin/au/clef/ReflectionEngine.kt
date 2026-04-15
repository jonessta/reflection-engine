package au.clef

import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaMethod

class ReflectionEngine {

    // ------------------------------ GUI methods ------------------------------------------------
    fun descriptor(function: KFunction<*>): MethodDescriptor {
        val method: Method =
            function.javaMethod ?: throw EngineException("Function '${function.name}' is not backed by a Java method")
        return buildMethods(listOf(method)).first()
    }

    fun descriptors(
        clazz: Class<*>, inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): List<MethodDescriptor> = buildMethods(collectMethods(clazz, inheritanceLevel))

    fun invokeDescriptor(descriptor: MethodDescriptor, instance: Any? = null, args: List<Any?>): Any? {
        if (!descriptor.isStatic && instance == null) {
            throw MissingInstanceException(descriptor.name)
        }
        return descriptor.invoke(ExecutionContext(instance), args)
    }

    // Optional GUI
    fun findDescriptorExact(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): MethodDescriptor {
        val methods: List<MethodDescriptor> = descriptors(clazz, inheritanceLevel)
        return methods.firstOrNull {
            val rawMethod: Method = it.rawMethod
            it.name == methodName && rawMethod.parameterTypes.toList() == parameterTypes
        } ?: throw MethodNotFoundException(
            owner = clazz,
            methodName = methodName,
            parameterTypes = parameterTypes,
            staticOnly = null,
            availableOverloads = methods.filter { it.name == methodName }.map { signature(it) })
    }

    // ------------------------- END GUI methods -------------------------------------------------------

    internal fun materialize(value: Any?, targetType: Class<*>): Any? =
        tryConvert(value, targetType)?.value ?: throw TypeMismatchException(value, targetType)

    internal fun buildObject(obj: Value.Object): Any {
        val clazz: Class<*> = obj.type
        return if (isKotlinClass(clazz)) {
            buildKotlinObject(obj)
        } else {
            buildJavaObject(obj)
        }
    }

    // ------------ internal ---------------------------------------------------

    internal fun collectMethods(clazz: Class<*>, level: InheritanceLevel): List<Method> {
        val result: MutableList<Method> = mutableListOf()
        var current: Class<*>? = clazz
        var depth = 0
        val maxDepth: Int = when (level) {
            is InheritanceLevel.DeclaredOnly -> 0
            is InheritanceLevel.All -> Int.MAX_VALUE
            is InheritanceLevel.Depth -> level.value
        }
        while (current != null && depth <= maxDepth) {
            result += current.declaredMethods
            current = current.superclass
            depth++
        }
        return result.filter { method: Method ->
            Modifier.isPublic(method.modifiers) && !method.isSynthetic && !method.isBridge
        }.distinctBy { method: Method ->
            "${method.name}(${method.parameterTypes.joinToString(",") { t: Class<*> -> t.name }})"
        }
    }

    internal fun buildMethods(methods: List<Method>): List<MethodDescriptor> = methods.map { method: Method ->
        val isStatic: Boolean = Modifier.isStatic(method.modifiers)
        val params: List<ParamDescriptor> = method.parameters.mapIndexed { i: Int, p: Parameter ->
            ParamDescriptor(
                index = i, name = p.name ?: "arg$i", type = p.type, nullable = true
            )
        }
        MethodDescriptor(
            name = method.name,
            parameters = params,
            returnType = method.returnType,
            isStatic = isStatic,
            rawMethod = method,
            invoke = { ctx: ExecutionContext, args: List<Any?> ->
                val convertedArgs: List<Any?> =
                    args.mapIndexed { i: Int, arg: Any? -> materialize(arg, params[i].type) }
                val target: Any? = when {
                    isStatic -> null
                    ctx.instance != null -> ctx.instance
                    else -> throw MissingInstanceException(method.name)
                }
                method.invoke(target, *convertedArgs.toTypedArray())
            })
    }

    // ------------ private ---------------------------------------------------

    private fun signature(m: MethodDescriptor): String =
        "${m.name}(${m.parameters.joinToString(", ") { it.type.simpleName }})"

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
                    return ConversionResult(built, 10)
                }
                return null
            }
            else -> value
        }
        if (unwrapped == null) {
            return if (targetType.isPrimitive) null else ConversionResult(null, 0)
        }
        return when (normalizedTarget) {
            // todo add Float and Short/Byte/Char ?
            Int::class.java -> when (unwrapped) {
                is Int -> ConversionResult(unwrapped, 0)
                is String -> unwrapped.toIntOrNull()?.let { ConversionResult(it, 1) }
                else -> null
            }
            Long::class.java -> when (unwrapped) {
                is Long -> ConversionResult(unwrapped, 0)
                is Int -> ConversionResult(unwrapped.toLong(), 1)
                is String -> unwrapped.toLongOrNull()?.let { ConversionResult(it, 1) }
                else -> null
            }
            Double::class.java -> when (unwrapped) {
                is Double -> ConversionResult(unwrapped, 0)
                is Int -> ConversionResult(unwrapped.toDouble(), 1)
                is Long -> ConversionResult(unwrapped.toDouble(), 1)
                is String -> unwrapped.toDoubleOrNull()?.let { ConversionResult(it, 2) }
                else -> null
            }
            String::class.java -> when (unwrapped) {
                is String -> ConversionResult(unwrapped, 0)
                else -> ConversionResult(unwrapped.toString(), 3)
            }
            Boolean::class.java -> when (unwrapped) {
                is Boolean -> ConversionResult(unwrapped, 0)
                is String -> when (unwrapped.lowercase()) {
                    "true" -> ConversionResult(true, 1)
                    "false" -> ConversionResult(false, 1)
                    else -> null
                }
                else -> null
            }
            else -> {
                if (normalizedTarget.isAssignableFrom(unwrapped.javaClass)) {
                    ConversionResult(unwrapped, 0)
                } else {
                    null
                }
            }
        }
    }

    private data class ConversionResult(val value: Any?, val score: Int)

    // Kotlin reflection
    private fun buildKotlinObject(obj: Value.Object): Any {
        val kClass: KClass<out Any> = obj.type.kotlin
        val ctor: KFunction<Any> = kClass.primaryConstructor ?: throw ObjectConstructionException(
            obj.type, "No primary constructor"
        )
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