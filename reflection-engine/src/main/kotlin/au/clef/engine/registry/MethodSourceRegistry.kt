package au.clef.engine.registry

import au.clef.engine.ExecutionContext
import au.clef.engine.ExecutionId
import au.clef.engine.MethodNotFoundException
import au.clef.engine.MethodSource
import au.clef.engine.model.IllegalMethodIdException
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.javaMethod

private data class RegistryEntry(
    val descriptor: MethodDescriptor,
    val javaMethod: Method
)

private data class ResolvedMethod(
    val kotlinFunction: KFunction<*>?,
    val javaMethod: Method
)

private data class ParsedMethodId(
    val declaringClassName: String,
    val methodName: String,
    val parameterTypeNames: List<String>
) {
    companion object {
        private val regex: Regex = Regex("""^([A-Za-z_][A-Za-z0-9_$.]*)#([A-Za-z_][A-Za-z0-9_$]*)\((.*)\)$""")

        fun parse(methodId: MethodId): ParsedMethodId {
            val match: MatchResult = regex.matchEntire(methodId.toString())
                ?: throw IllegalMethodIdException("Expected <class>#<method>(<paramTypes>)")

            val (className: String, methodName: String, params: String) = match.destructured
            val parameterTypeNames: List<String> = if (params.isBlank()) emptyList() else params.split(",")

            return ParsedMethodId(
                declaringClassName = className,
                methodName = methodName,
                parameterTypeNames = parameterTypeNames
            )
        }
    }
}

class MethodSourceRegistry(
    methodSources: Collection<MethodSource>,
    methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    private val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) : MethodSourceTypes {

    private val descriptorsByClass: MutableMap<Class<*>, MutableList<MethodDescriptor>> = LinkedHashMap()

    private val entriesById: MutableMap<MethodId, RegistryEntry> = LinkedHashMap()

    private val executionContextsById: MutableMap<ExecutionId, ExecutionContext> = LinkedHashMap()

    private val javaMethodsByClass: MutableMap<Class<*>, List<Method>> = LinkedHashMap()

    private val kotlinFunctionsByClass: MutableMap<Class<*>, List<KFunction<*>>> = LinkedHashMap()

    override val declaringClasses: List<Class<*>> = methodSources
        .map { source: MethodSource -> source.declaringClass.java }
        .distinct()

    override val knownClasses: List<Class<*>> =
        (methodSources.map { source: MethodSource -> source.declaringClass } + methodSupportingTypes)
            .distinct()
            .map { kClass: KClass<*> -> kClass.java }

    init {
        require(methodSources.isNotEmpty()) { "methodSources must not be empty" }
        methodSources.forEach { source: MethodSource -> registerMethodSource(source) }
    }

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        descriptorsByClass[clazz]?.toList()
            ?: throw IllegalArgumentException("Not registered: ${clazz.name}")

    fun descriptor(id: MethodId): MethodDescriptor = entriesById[id]?.descriptor ?: throwMethodNotFound(id)

    fun method(id: MethodId): Method = entriesById[id]?.javaMethod ?: throwMethodNotFound(id)

    fun executionContext(id: ExecutionId): ExecutionContext =
        executionContextsById[id] ?: throw IllegalArgumentException("Unknown ID: $id")

    fun allDescriptors(): List<MethodDescriptor> = entriesById.values.map { entry: RegistryEntry -> entry.descriptor }

    fun allExecutionContexts(): List<ExecutionContext> = executionContextsById.values.toList()

    private fun registerMethodSource(source: MethodSource) {
        val clazz: Class<*> = source.declaringClass.java
        when (source) {
            is MethodSource.StaticClass -> {
                registerMethods(
                    clazz = clazz,
                    requireStatic = true,
                    executionContextFor = { methodId: MethodId -> ExecutionContext.Static(methodId) }
                )
            }

            is MethodSource.Instance -> {
                registerMethods(
                    clazz = clazz,
                    requireStatic = false,
                    executionContextFor = { methodId: MethodId ->
                        ExecutionContext.Instance(
                            instance = source.instance,
                            instanceDescription = source.instanceDescription,
                            methodId = methodId
                        )
                    }
                )
            }

            is MethodSource.StaticMethod -> {
                registerSingleMethod(
                    clazz = clazz,
                    methodId = source.methodId,
                    requireStatic = true,
                    executionContextFor = { methodId: MethodId -> ExecutionContext.Static(methodId) }
                )
            }

            is MethodSource.InstanceMethod -> {
                registerSingleMethod(
                    clazz = clazz,
                    methodId = source.methodId,
                    requireStatic = false,
                    executionContextFor = { methodId: MethodId ->
                        ExecutionContext.Instance(
                            instance = source.instance,
                            instanceDescription = source.instanceDescription,
                            methodId = methodId
                        )
                    }
                )
            }
        }
    }

    private fun registerMethods(
        clazz: Class<*>,
        requireStatic: Boolean,
        executionContextFor: (MethodId) -> ExecutionContext
    ) {
        val descriptors: MutableList<MethodDescriptor> = descriptorsByClass.getOrPut(clazz) { mutableListOf() }
        val kotlinFunctionsByJavaMethod: Map<Method, KFunction<*>> =
            collectHierarchyFunctions(clazz)
                .mapNotNull { function: KFunction<*> ->
                    function.javaMethod?.let { javaMethod: Method -> javaMethod to function }
                }
                .toMap()

        for (javaMethod: Method in collectHierarchyMethods(clazz)) {
            val isStaticMethod: Boolean = Modifier.isStatic(javaMethod.modifiers)
            if (isStaticMethod != requireStatic) {
                continue
            }

            val methodId: MethodId = MethodId.from(javaMethod)
            if (entriesById.containsKey(methodId)) {
                continue
            }

            val kotlinFunction: KFunction<*>? = kotlinFunctionsByJavaMethod[javaMethod]

            val descriptor: MethodDescriptor =
                kotlinFunction?.let { function: KFunction<*> ->
                    MethodDescriptor.from(function, javaMethod, methodId)
                } ?: MethodDescriptor.from(javaMethod)

            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(
                descriptor = descriptor,
                javaMethod = javaMethod
            )

            val executionContext: ExecutionContext = executionContextFor(methodId)
            executionContextsById[executionContext.executionId] = executionContext
        }
    }

    private fun registerSingleMethod(
        clazz: Class<*>,
        methodId: MethodId,
        requireStatic: Boolean,
        executionContextFor: (MethodId) -> ExecutionContext
    ) {
        val resolved: ResolvedMethod = resolveMethod(clazz, methodId)
        val javaMethod: Method = resolved.javaMethod

        if (requireStatic) {
            require(Modifier.isStatic(javaMethod.modifiers)) { "Method ${methodId.value} must be static" }
        } else {
            require(!Modifier.isStatic(javaMethod.modifiers)) { "Method ${methodId.value} must be an instance method" }
        }

        val descriptors: MutableList<MethodDescriptor> = descriptorsByClass.getOrPut(clazz) { mutableListOf() }

        if (!entriesById.containsKey(methodId)) {
            val descriptor: MethodDescriptor =
                resolved.kotlinFunction?.let { kotlinFunction: KFunction<*> ->
                    MethodDescriptor.from(kotlinFunction, javaMethod, methodId)
                } ?: run {
                    val parsed: ParsedMethodId = ParsedMethodId.parse(methodId)
                    MethodDescriptor.from(javaMethod, methodId, parsed.methodName)
                }

            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(descriptor = descriptor, javaMethod = javaMethod)
        }

        // Intentionally allow multiple execution contexts for the same registered method,
        // for example when the same instance method is exposed on multiple object instances.
        val executionContext: ExecutionContext = executionContextFor(methodId)
        executionContextsById[executionContext.executionId] = executionContext
    }

    private fun resolveMethod(clazz: Class<*>, methodId: MethodId): ResolvedMethod {
        val javaMethods: List<Method> = collectHierarchyMethods(clazz)
        val kotlinFunctions: List<KFunction<*>> = collectHierarchyFunctions(clazz)

        val resolvedJavaMethod: Method? =
            javaMethods.firstOrNull { javaMethod: Method -> MethodId.from(javaMethod) == methodId }

        if (resolvedJavaMethod != null) {
            val kotlinFunction: KFunction<*>? =
                kotlinFunctions.firstOrNull { function: KFunction<*> -> function.javaMethod == resolvedJavaMethod }
            return ResolvedMethod(kotlinFunction = kotlinFunction, javaMethod = resolvedJavaMethod)
        }

        val parsed: ParsedMethodId = ParsedMethodId.parse(methodId)

        if (parsed.declaringClassName != clazz.name) {
            throwMethodNotFound(methodId)
        }

        val kotlinFunction: KFunction<*>? =
            kotlinFunctions.firstOrNull { function: KFunction<*> ->
                function.name == parsed.methodName &&
                        valueParameterTypeNames(function) == parsed.parameterTypeNames &&
                        function.javaMethod != null
            }

        if (kotlinFunction != null) {
            return ResolvedMethod(
                kotlinFunction = kotlinFunction,
                javaMethod = requireNotNull(kotlinFunction.javaMethod)
            )
        }

        throwMethodNotFound(methodId)
    }

    private fun collectHierarchyFunctions(clazz: Class<*>): List<KFunction<*>> =
        kotlinFunctionsByClass.getOrPut(clazz) {
            hierarchyFor(clazz.kotlin)
                .flatMap { current: KClass<*> -> current.declaredMemberFunctions.asSequence() }
                .filter { function: KFunction<*> ->
                    val javaMethod: Method? = function.javaMethod
                    javaMethod != null &&
                            Modifier.isPublic(javaMethod.modifiers) &&
                            javaMethod.declaringClass != Any::class.java &&
                            !javaMethod.isSynthetic &&
                            !javaMethod.isBridge
                }
                .distinctBy { function: KFunction<*> ->
                    "${function.name}(${valueParameterTypeNames(function).joinToString(",")})"
                }
                .sortedBy { function: KFunction<*> ->
                    val javaMethod: Method = requireNotNull(function.javaMethod) {
                        "Function ${function.name} does not have a Java method"
                    }
                    MethodId.from(javaMethod).value
                }
                .toList()
        }

    private fun collectHierarchyMethods(clazz: Class<*>): List<Method> = javaMethodsByClass.getOrPut(clazz) {
        hierarchyFor(clazz)
            .filter { current: Class<*> -> current != Any::class.java }
            .flatMap { current: Class<*> -> current.declaredMethods.asSequence() }
            .filter { javaMethod: Method ->
                Modifier.isPublic(javaMethod.modifiers) &&
                        !javaMethod.isSynthetic &&
                        !javaMethod.isBridge
            }
            .distinctBy { javaMethod: Method -> MethodId.from(javaMethod) }
            .sortedBy { javaMethod: Method -> MethodId.from(javaMethod).value }
            .toList()
    }

    private fun hierarchyFor(root: KClass<*>): Sequence<KClass<*>> {
        val hierarchy: Sequence<KClass<*>> =
            generateSequence(root) { current: KClass<*> -> current.java.superclass?.kotlin }

        return when (inheritanceLevel) {
            InheritanceLevel.DeclaredOnly -> hierarchy.take(1)
            InheritanceLevel.All -> hierarchy
            is InheritanceLevel.Depth -> hierarchy.take(inheritanceLevel.value + 1)
        }
    }

    private fun hierarchyFor(root: Class<*>): Sequence<Class<*>> {
        val hierarchy: Sequence<Class<*>> = generateSequence(root) { current: Class<*> -> current.superclass }

        return when (inheritanceLevel) {
            InheritanceLevel.DeclaredOnly -> hierarchy.take(1)
            InheritanceLevel.All -> hierarchy
            is InheritanceLevel.Depth -> hierarchy.take(inheritanceLevel.value + 1)
        }
    }

    private fun valueParameterTypeNames(function: KFunction<*>): List<String> {
        val javaMethod: Method = function.javaMethod ?: error("Function ${function.name} does not have a Java method")
        return javaMethod.parameterTypes.map { parameterType: Class<*> ->
            parameterType.name
        }
    }

    private fun throwMethodNotFound(methodId: MethodId): Nothing =
        throw MethodNotFoundException(
            methodId = methodId,
            available = entriesById.keys.map { id: MethodId -> id.toString() }
        )
}