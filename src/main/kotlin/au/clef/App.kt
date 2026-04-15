package au.clef

import java.lang.reflect.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

class TypeMismatchException(string: String) : Exception(string)

class App {

    fun descriptors(
        clazz: Class<*>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): List<MethodDescriptor> =
        buildMethods(collectMethods(clazz, inheritanceLevel))

    fun findDescriptorExact(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): MethodDescriptor =
        descriptors(clazz, inheritanceLevel).firstOrNull {
            it.name == methodName &&
                    it.rawMethod.parameterTypes.contentEquals(parameterTypes.toTypedArray())
        } ?: throw IllegalArgumentException(
            "No method '$methodName(${parameterTypes.joinToString(", ") { it.simpleName }})' found on ${clazz.name}"
        )

    fun invokeDescriptor(descriptor: MethodDescriptor, instance: Any? = null, args: List<Any?>): Any? {
        require(descriptor.isStatic || instance != null) { "Instance required for method ${descriptor.name}" }
        return descriptor.invoke(ExecutionContext(instance), args)
    }

    fun call(
        target: Any,
        methodName: String,
        args: List<Any?>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        val methods: List<MethodDescriptor> = buildMethods(collectMethods(target.javaClass, inheritanceLevel))
            .filter { it.name == methodName }

        if (methods.isEmpty()) {
            throw IllegalArgumentException("No method '$methodName' found on ${target.javaClass.name}")
        }

        val scored: List<Pair<MethodDescriptor, Int>> = methods.mapNotNull { method ->
            matchScore(method, args)?.let { score -> method to score }
        }

        if (scored.isEmpty()) {
            throw IllegalArgumentException("No matching overload for '$methodName' on ${target.javaClass.name}")
        }

        val bestScore = scored.minOf { it.second }
        val best: List<Pair<MethodDescriptor, Int>> = scored.filter { it.second == bestScore }

        if (best.size > 1) {
            throw IllegalArgumentException(
                "Ambiguous call to '$methodName'. Matching overloads: ${best.joinToString(" | ") { signature(it.first) }}"
            )
        }

        return best.first().first.invoke(ExecutionContext(target), args)
    }

    fun callStatic(
        clazz: Class<*>,
        methodName: String,
        args: List<Any?>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        val methods: List<MethodDescriptor> = buildMethods(collectMethods(clazz, inheritanceLevel))
            .filter { it.name == methodName && it.isStatic }
        if (methods.isEmpty()) {
            throw IllegalArgumentException("No static method '$methodName' found on ${clazz.name}")
        }
        val scored: List<Pair<MethodDescriptor, Int>> = methods.mapNotNull { method ->
            matchScore(method, args)?.let { score -> method to score }
        }
        if (scored.isEmpty()) {
            throw IllegalArgumentException("No matching static overload for '$methodName' on ${clazz.name}")
        }
        val bestScore = scored.minOf { it.second }
        val best: List<Pair<MethodDescriptor, Int>> = scored.filter { it.second == bestScore }
        if (best.size > 1) {
            throw IllegalArgumentException(
                "Ambiguous static call to '$methodName'. Matching overloads: ${best.joinToString(" | ") { signature(it.first) }}"
            )
        }
        return best.first().first.invoke(ExecutionContext(null), args)
    }

    fun callStaticExact(
        clazz: Class<*>,
        methodName: String,
        args: List<Any?>,
        paramTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        require(args.size == paramTypes.size) { "args size (${args.size}) must match parameterTypes size (${paramTypes.size})" }
        val methods: List<MethodDescriptor> = buildMethods(collectMethods(clazz, inheritanceLevel))
        val matchingMethods: List<MethodDescriptor> = methods.filter { it.name == methodName && it.isStatic }
        val method: MethodDescriptor = matchingMethods.firstOrNull {
            it.rawMethod.parameterTypes.contentEquals(paramTypes.toTypedArray())
        } ?: throw IllegalArgumentException(
            "No static method '$methodName(${paramTypes.joinToString(", ") { it.simpleName }})' found on ${clazz.name}"
        )
        return method.invoke(ExecutionContext(null), args)
    }

    fun callExact(
        target: Any,
        methodName: String,
        args: List<Any?>,
        paramTypes: List<Class<*>>,
        inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
    ): Any? {
        require(args.size == paramTypes.size) { "args size (${args.size}) must match parameterTypes size (${paramTypes.size})" }
        val collectedMethods: List<Method> = collectMethods(target.javaClass, inheritanceLevel)
        val methods: List<MethodDescriptor> = buildMethods(collectedMethods)
        val matchingMethods: List<MethodDescriptor> = methods.filter { it.name == methodName }
        val method: MethodDescriptor = matchingMethods.firstOrNull {
            it.rawMethod.parameterTypes.contentEquals(paramTypes.toTypedArray())
        } ?: throw IllegalArgumentException(
            buildString {
                append("No method '")
                append(methodName)
                append("(")
                append(paramTypes.joinToString(", ") { it: Class<*> -> it.simpleName })
                append(")' found on ")
                append(target.javaClass.name)
                if (matchingMethods.isNotEmpty()) {
                    append(". Available overloads: ")
                    append(
                        matchingMethods.joinToString(" | ") { m ->
                            "${m.name}(${m.rawMethod.parameterTypes.joinToString(", ") { it.simpleName }})"
                        }
                    )
                }
            }
        )
        return method.invoke(ExecutionContext(target), args)
    }

    fun materialize(value: Any?, targetType: Class<*>): Any? =
        tryConvert(value, targetType)?.value
            ?: throw TypeMismatchException("Cannot convert $value to ${targetType.simpleName}")

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
        var depth = 0
        val maxDepth = when (level) {
            is InheritanceLevel.DeclaredOnly -> 0
            is InheritanceLevel.All -> Int.MAX_VALUE
            is InheritanceLevel.Depth -> level.value
        }
        while (current != null && depth <= maxDepth) {
            result += current.declaredMethods
            current = current.superclass
            depth++
        }
        return result
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.isBridge }
            .distinctBy { it.name + it.parameterTypes.joinToString { t -> t.name } }
    }

    internal fun buildMethods(methods: List<Method>): List<MethodDescriptor> {
        return methods.map { method ->
            val isStatic = Modifier.isStatic(method.modifiers)
            val params: List<ParamDescriptor> = method.parameters.mapIndexed { i, p ->
                ParamDescriptor(index = i, name = p.name ?: "arg$i", type = p.type, nullable = true)
            }

            MethodDescriptor(
                name = method.name,
                parameters = params,
                returnType = method.returnType,
                isStatic = isStatic,
                rawMethod = method,
                invoke = { ctx, args ->
                    val convertedArgs: List<Any?> = args.mapIndexed { i, arg ->
                        materialize(arg, params[i].type)
                    }
                    val target = when {
                        isStatic -> null
                        ctx.instance != null -> ctx.instance
                        else -> throw IllegalStateException("Instance required for ${method.name}")
                    }
                    method.invoke(target, *convertedArgs.toTypedArray())
                }
            )
        }
    }

    // ------------ private ---------------------------------------------------

    private fun signature(m: MethodDescriptor): String =
        "${m.name}(${m.parameters.joinToString(", ") { it.type.simpleName }})"

    private fun conversionScore(value: Any?, targetType: Class<*>): Int? = tryConvert(value, targetType)?.score

    private fun matchScore(method: MethodDescriptor, args: List<Any?>): Int? {
        if (method.parameters.size != args.size)
            return null
        var total = 0
        for (i in method.parameters.indices) {
            val score = conversionScore(args[i], method.parameters[i].type) ?: return null
            total += score
        }
        return total
    }

    private fun normalize(type: Class<*>): Class<*> =
        when (type) {
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
        val unwrapped = when (value) {
            is Value.Primitive -> value.value
            is Value.Instance -> value.obj
            is Value.Object -> {
                val built = buildObject(value)
                if (normalizedTarget.isAssignableFrom(built.javaClass)) {
                    return ConversionResult(built, 10)
                }
                return null
            }

            else -> value
        }
        if (unwrapped == null) {
            return if (targetType.isPrimitive)
                null
            else
                ConversionResult(null, 0)
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
                } else null
            }
        }
    }

    private data class ConversionResult(val value: Any?, val score: Int)

    // Kotlin reflection (clean, reliable)
    private fun buildKotlinObject(obj: Value.Object): Any {
        val kClass: KClass<*> = obj.type.kotlin
        val ctor: KFunction<Any> =
            kClass.primaryConstructor ?: throw IllegalArgumentException("No primary constructor for ${obj.type.name}")
        val argsByParam: MutableMap<KParameter, Any?> = mutableMapOf()
        for (param: KParameter in ctor.parameters) {
            val name = param.name ?: throw IllegalArgumentException("Unnamed constructor parameter in ${obj.type.name}")
            val rawValue: Value? = obj.fields[name]
            if (rawValue == null) {
                if (param.isOptional)
                    continue
                if (param.type.isMarkedNullable) {
                    argsByParam[param] = null
                    continue
                }
                throw IllegalArgumentException("Missing field '$name' for ${obj.type.name}")
            }
            val paramClass: Class<out Any> = (param.type.classifier as? KClass<*>)?.java
                ?: throw IllegalArgumentException("Unsupported type for '$name' in ${obj.type.name}")
            argsByParam[param] = materialize(rawValue, paramClass)
        }
        return ctor.callBy(argsByParam)
    }

    // Java reflection fallback
    private fun buildJavaObject(obj: Value.Object): Any {
        val clazz: Class<*> = obj.type
        for (ctor: Constructor<*> in clazz.declaredConstructors) {
            val params = ctor.parameters
            if (params.size != obj.fields.size) continue
            try {
                val args: Array<Any?> = params.mapIndexed { i, param ->
                    val value: Value =
                        obj.fields[param.name]
                            ?: obj.fields["arg$i"]
                            ?: obj.fields.values.elementAtOrNull(i)
                            ?: throw IllegalArgumentException("Missing value for param $i")
                    materialize(value, param.type)
                }.toTypedArray()

                ctor.isAccessible = true
                return ctor.newInstance(*args)
            } catch (_: Exception) {
            }
        }

        val noArgCtor: Constructor<*> = clazz.declaredConstructors.firstOrNull { it.parameterCount == 0 }
            ?: throw IllegalArgumentException("Could not construct ${clazz.name}: no suitable constructor")

        noArgCtor.isAccessible = true
        val instance = noArgCtor.newInstance()
        obj.fields.forEach { (name: String, value: Value) ->
            runCatching {
                val field: Field = clazz.getDeclaredField(name)
                field.isAccessible = true
                field.set(instance, materialize(value, field.type))
            }
        }
        return instance
    }

    private fun isKotlinClass(clazz: Class<*>): Boolean = clazz.getAnnotation(Metadata::class.java) != null
}