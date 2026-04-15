package au.clef

import java.lang.reflect.*
import kotlin.reflect.*
import kotlin.reflect.full.primaryConstructor

class App {

    // ------------------------------ GUI methods ------------------------------------------------
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
            it.name == methodName && rawMethod.parameterTypes.contentEquals(parameterTypes.toTypedArray())
        } ?: throw MethodNotFoundException(
            owner = clazz,
            methodName = methodName,
            parameterTypes = parameterTypes,
            staticOnly = null,
            availableOverloads = methods.filter { it.name == methodName }.map { signature(it) })
    }

    // ------------------------- END GUI methods -------------------------------------------------------

    fun call(
        target: Any,
        methodName: String,
        args: List<Any?>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        val method: MethodDescriptor = resolveBestDescriptor(
            methods = descriptors(target.javaClass, inheritanceLevel),
            methodName = methodName,
            args = args,
            staticOnly = false,
            owner = target.javaClass
        )
        return method.invoke(ExecutionContext(target), args)
    }

    fun callStatic(
        clazz: Class<*>,
        methodName: String,
        args: List<Any?>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        val method: MethodDescriptor = resolveBestDescriptor(
            methods = descriptors(clazz, inheritanceLevel),
            methodName = methodName,
            args = args,
            staticOnly = true,
            owner = clazz
        )
        return method.invoke(ExecutionContext(null), args)
    }

    fun callStaticExact(
        clazz: Class<*>,
        methodName: String,
        args: List<Any?>,
        parameterTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        if (args.size != parameterTypes.size) {
            throw ArgumentCountMismatchException(
                expected = parameterTypes.size, actual = args.size, methodName = methodName, owner = clazz
            )
        }
        val methods: List<MethodDescriptor> =
            descriptors(clazz, inheritanceLevel).filter { it.name == methodName && it.isStatic }
        val method: MethodDescriptor = methods.firstOrNull {
            it.rawMethod.parameterTypes.contentEquals(parameterTypes.toTypedArray())
        } ?: throw MethodNotFoundException(
            owner = clazz,
            methodName = methodName,
            parameterTypes = parameterTypes,
            staticOnly = true,
            availableOverloads = methods.map { signature(it) })
        return method.invoke(ExecutionContext(null), args)
    }

    fun callExact(
        target: Any,
        methodName: String,
        args: List<Any?>,
        parameterTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        if (args.size != parameterTypes.size) {
            throw ArgumentCountMismatchException(
                expected = parameterTypes.size, actual = args.size, methodName = methodName, owner = target.javaClass
            )
        }
        val methods: List<MethodDescriptor> =
            descriptors(target.javaClass, inheritanceLevel).filter { it.name == methodName && !it.isStatic }
        val method: MethodDescriptor = methods.firstOrNull {
            it.rawMethod.parameterTypes.contentEquals(parameterTypes.toTypedArray())
        } ?: throw MethodNotFoundException(
            owner = target.javaClass,
            methodName = methodName,
            parameterTypes = parameterTypes,
            staticOnly = false,
            availableOverloads = methods.map { signature(it) })
        return method.invoke(ExecutionContext(target), args)
    }

    fun materialize(value: Any?, targetType: Class<*>): Any? =
        tryConvert(value, targetType)?.value ?: throw TypeMismatchException(value, targetType)

    fun buildObject(obj: Value.Object): Any {
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
        var depth: Int = 0
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
        return result.filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.isBridge }
            .distinctBy { "${it.name}(${it.parameterTypes.joinToString(",") { t -> t.name }})" }
    }

    internal fun buildMethods(methods: List<Method>): List<MethodDescriptor> = methods.map { method: Method ->
        val isStatic: Boolean = Modifier.isStatic(method.modifiers)
        val params: List<ParamDescriptor> = method.parameters.mapIndexed { i: Int, p ->
            ParamDescriptor(index = i, name = p.name ?: "arg$i", type = p.type, nullable = true)
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

    private fun resolveBestDescriptor(
        methods: List<MethodDescriptor>, methodName: String, args: List<Any?>, staticOnly: Boolean, owner: Class<*>
    ): MethodDescriptor {
        val candidates: List<MethodDescriptor> = methods.filter {
            it.name == methodName && it.isStatic == staticOnly
        }
        if (candidates.isEmpty()) {
            throw MethodNotFoundException(
                owner = owner, methodName = methodName, parameterTypes = null, staticOnly = staticOnly
            )
        }
        val scored: List<Pair<MethodDescriptor, Int>> = candidates.mapNotNull { method: MethodDescriptor ->
            matchScore(method, args)?.let { score: Int -> method to score }
        }
        if (scored.isEmpty()) {
            throw MethodNotFoundException(
                owner = owner,
                methodName = methodName,
                parameterTypes = null,
                staticOnly = staticOnly,
                availableOverloads = candidates.map { signature(it) })
        }
        val bestScore: Int = scored.minOf { it.second }
        val best: List<Pair<MethodDescriptor, Int>> = scored.filter { it.second == bestScore }
        if (best.size > 1) {
            throw AmbiguousMethodException(
                owner = owner,
                methodName = methodName,
                candidates = best.map { signature(it.first) },
                staticOnly = staticOnly
            )
        }
        return best.first().first
    }

    private fun conversionScore(value: Any?, targetType: Class<*>): Int? = tryConvert(value, targetType)?.score

    private fun matchScore(method: MethodDescriptor, args: List<Any?>): Int? {
        if (method.parameters.size != args.size) return null
        var total: Int = 0
        for (i: Int in method.parameters.indices) {
            val score: Int = conversionScore(args[i], method.parameters[i].type) ?: return null
            total += score
        }
        return total
    }

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