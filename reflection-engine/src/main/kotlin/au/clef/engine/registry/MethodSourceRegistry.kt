package au.clef.engine.registry

import au.clef.engine.ExecutionContext
import au.clef.engine.MethodNotFoundException
import au.clef.engine.MethodSource
import au.clef.engine.ReflectionConfig
import au.clef.engine.model.ExecutionId
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import au.clef.engine.model.ParamDescriptor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.jvm.javaMethod

class MethodSourceRegistry(
    private val methodSources: Collection<MethodSource>,
    private val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) {

    constructor(reflectionConfig: ReflectionConfig) : this(
        methodSources = reflectionConfig.methodSources,
        inheritanceLevel = reflectionConfig.inheritanceLevel
    )

    private data class RegistryEntry(
        val sourceDeclaringClass: Class<*>,
        val method: Method,
        val descriptor: MethodDescriptor,
        val executionContext: ExecutionContext
    )

    private val entriesById: MutableMap<MethodId, RegistryEntry> = LinkedHashMap()

    private val executionContextsById: MutableMap<String, ExecutionContext> = LinkedHashMap()

    val declaringClasses: List<Class<*>> = methodSources
        .map { source: MethodSource -> source.declaringClass.java }
        .distinct()

    init {
        methodSources.forEach { source: MethodSource -> registerMethodSource(source) }
    }

    fun executionContexts(): List<ExecutionContext> = executionContextsById.values.toList()

    fun executionContext(executionId: ExecutionId): ExecutionContext =
        requireNotNull(executionContextsById[executionId.value]) {
            "Unknown ID: $executionId"
        }

    fun descriptor(methodId: MethodId): MethodDescriptor =
        entriesById[methodId]?.descriptor ?: throwMethodNotFound(methodId)

    fun descriptors(declaringClass: Class<*>): List<MethodDescriptor> = entriesById.values
        .asSequence()
        .filter { entry: RegistryEntry -> entry.sourceDeclaringClass == declaringClass }
        .map { entry: RegistryEntry -> entry.descriptor }
        .toList()

    fun allDescriptors(): List<MethodDescriptor> = entriesById.values
        .map { entry: RegistryEntry -> entry.descriptor }

    fun exposedMethods(): List<Method> = entriesById.values
        .map { entry: RegistryEntry -> entry.method }

    fun method(id: MethodId): Method = entriesById[id]?.method ?: throwMethodNotFound(id)

    private fun registerMethodSource(source: MethodSource) {
        when (source) {
            is MethodSource.StaticClass -> {
                candidateMethods(
                    declaringClass = source.declaringClass.java,
                    wantStatic = true
                ).forEach { method: Method -> registerResolvedMethod(source, method) }
            }

            is MethodSource.StaticMethod -> {
                val method: Method = resolveConfiguredMethod(
                    declaringClass = source.declaringClass.java,
                    methodId = source.methodId,
                    wantStatic = true
                )
                registerResolvedMethod(source, method)
            }

            is MethodSource.Instance -> {
                candidateMethods(
                    declaringClass = source.instance::class.java,
                    wantStatic = false
                ).forEach { method: Method -> registerResolvedMethod(source, method) }
            }

            is MethodSource.InstanceMethod -> {
                val method: Method = resolveConfiguredMethod(
                    declaringClass = source.instance::class.java,
                    methodId = source.methodId,
                    wantStatic = false
                )
                registerResolvedMethod(source, method)
            }
        }
    }

    private fun registerResolvedMethod(source: MethodSource, method: Method) {
        val methodId: MethodId = MethodId.from(method)
        val descriptor: MethodDescriptor = toMethodDescriptor(method)

        val executionContext: ExecutionContext =
            when (source) {
                is MethodSource.StaticClass,
                is MethodSource.StaticMethod -> ExecutionContext.Static(
                    sourceDescription = source.sourceDescription,
                    methodId = methodId
                )

                is MethodSource.Instance -> ExecutionContext.Instance(
                    instance = source.instance,
                    sourceDescription = source.sourceDescription,
                    methodId = methodId
                )

                is MethodSource.InstanceMethod -> ExecutionContext.Instance(
                    instance = source.instance,
                    sourceDescription = source.sourceDescription,
                    methodId = methodId
                )
            }

        entriesById[methodId] = RegistryEntry(
            sourceDeclaringClass = source.declaringClass.java,
            method = method,
            descriptor = descriptor,
            executionContext = executionContext
        )
        executionContextsById[executionContext.executionId.value] = executionContext
    }

    private fun resolveConfiguredMethod(
        declaringClass: Class<*>,
        methodId: MethodId,
        wantStatic: Boolean
    ): Method {
        val matches: List<Method> = candidateMethods(declaringClass, wantStatic)
            .filter { candidate: Method -> MethodId.from(candidate) == methodId }

        return when (matches.size) {
            1 -> matches.single()

            0 -> {
                val available: String = candidateMethods(declaringClass, wantStatic)
                    .joinToString(", ") { candidate: Method ->
                        MethodId.from(candidate).toString()
                    }

                throw IllegalArgumentException(
                    "Method '$methodId' not found on ${declaringClass.name}. Available methods: $available"
                )
            }

            else -> {
                val matched: String = matches
                    .joinToString(", ") { candidate: Method -> candidate.toString() }

                throw IllegalArgumentException(
                    "Method '$methodId' is ambiguous on ${declaringClass.name}. Matches: $matched"
                )
            }
        }
    }

    private fun candidateMethods(
        declaringClass: Class<*>,
        wantStatic: Boolean
    ): List<Method> = methodSequenceForInheritance(declaringClass)
        .filter { method: Method -> Modifier.isStatic(method.modifiers) == wantStatic }
        .filter { method: Method -> Modifier.isPublic(method.modifiers) }
        .filter { method: Method -> !method.isSynthetic && !method.isBridge }
        .distinctBy { method: Method -> MethodId.from(method) }
        .toList()

    private fun methodSequenceForInheritance(declaringClass: Class<*>): Sequence<Method> =
        when (inheritanceLevel) {
            InheritanceLevel.DeclaredOnly -> declaringClass.declaredMethods.asSequence()

            InheritanceLevel.All -> classHierarchy(declaringClass).asSequence()
                .flatMap { current: Class<*> -> current.declaredMethods.asSequence() }

            is InheritanceLevel.Depth -> classHierarchy(declaringClass)
                .take(inheritanceLevel.depth + 1)
                .asSequence()
                .flatMap { current: Class<*> -> current.declaredMethods.asSequence() }
        }

    private fun classHierarchy(start: Class<*>): List<Class<*>> {
        val result = mutableListOf<Class<*>>()
        var current: Class<*>? = start
        while (current != null) {
            result += current
            current = current.superclass
        }
        return result
    }

    private fun toMethodDescriptor(method: Method): MethodDescriptor {
        val methodId: MethodId = MethodId.from(method)
        val kotlinFunction = kotlinFunction(method)

        val kotlinValueParameters =
            kotlinFunction?.parameters
                ?.filter { parameter -> parameter.kind == kotlin.reflect.KParameter.Kind.VALUE }
                ?: emptyList()

        val parameters: List<ParamDescriptor> =
            method.parameters.mapIndexed { index, parameter ->
                val kotlinParameter = kotlinValueParameters.getOrNull(index)
                val logicalType =
                    (kotlinParameter?.type?.classifier as? kotlin.reflect.KClass<*>)?.java
                        ?: parameter.type

                ParamDescriptor(
                    index = index,
                    logicalType = logicalType,
                    runtimeType = parameter.type,
                    reflectedName = parameter.name,
                    name = parameter.name,
                    nullable = kotlinParameter?.type?.isMarkedNullable
                        ?: !parameter.type.isPrimitive
                )
            }

        return MethodDescriptor(
            id = methodId,
            reflectedName = kotlinFunction?.name ?: method.name,
            displayName = null,
            returnType = method.returnType,
            isStatic = Modifier.isStatic(method.modifiers),
            parameters = parameters
        )
    }

    private fun kotlinFunction(method: Method): kotlin.reflect.KFunction<*>? =
        method.declaringClass.kotlin.members
            .filterIsInstance<kotlin.reflect.KFunction<*>>()
            .firstOrNull { function -> function.javaMethod == method }

    private fun throwMethodNotFound(methodId: MethodId): Nothing = throw MethodNotFoundException(
        methodId = methodId,
        available = entriesById.keys.map { id: MethodId -> id.toString() }
    )
}