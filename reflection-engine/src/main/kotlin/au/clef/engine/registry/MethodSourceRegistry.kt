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
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
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
        private val regex: Regex = Regex("""^([\w$.]+)#(\w+)\((.*)\)$""")

        fun parse(methodId: MethodId): ParsedMethodId {
            val match: MatchResult = regex.matchEntire(methodId.toString())
                ?: throw IllegalMethodIdException("Expected <class>#<method>(<paramTypes>)")

            val (className: String, methodName: String, params: String) = match.destructured

            val parameterTypeNames: List<String> =
                if (params.isBlank()) {
                    emptyList()
                } else {
                    params.split(",")
                }

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

    private val descriptorsByClass: MutableMap<Class<*>, MutableList<MethodDescriptor>> =
        ConcurrentHashMap()

    private val entriesById: MutableMap<MethodId, RegistryEntry> =
        ConcurrentHashMap()

    private val executionContextsById: MutableMap<ExecutionId, ExecutionContext> =
        ConcurrentHashMap()

    override val declaringClasses: List<Class<*>> =
        methodSources.map { source: MethodSource -> source.declaringClass.java }.distinct()

    override val knownClasses: List<Class<*>> =
        (methodSources.map { source: MethodSource -> source.declaringClass } + methodSupportingTypes)
            .distinct()
            .map { kClass: KClass<*> -> kClass.java }

    init {
        require(methodSources.isNotEmpty()) { "methodSources must not be empty" }
        methodSources.forEach { source: MethodSource ->
            registerMethodSource(source)
        }
    }

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        descriptorsByClass[clazz]?.toList()
            ?: throw IllegalArgumentException("Not registered: ${clazz.name}")

    fun descriptor(id: MethodId): MethodDescriptor =
        entriesById[id]?.descriptor ?: throwMethodNotFound(id)

    fun method(id: MethodId): Method =
        entriesById[id]?.javaMethod ?: throwMethodNotFound(id)

    fun executionContext(id: ExecutionId): ExecutionContext =
        executionContextsById[id] ?: throw IllegalArgumentException("Unknown ID: $id")

    fun allDescriptors(): List<MethodDescriptor> =
        entriesById.values.map { entry: RegistryEntry -> entry.descriptor }

    fun allExecutionContexts(): List<ExecutionContext> =
        executionContextsById.values.toList()

    private fun registerMethodSource(source: MethodSource): Unit {
        val clazz: Class<*> = source.declaringClass.java

        when (source) {
            is MethodSource.StaticClass -> {
                registerMethods(
                    clazz = clazz,
                    requireStatic = true,
                    executionContextFor = { methodId: MethodId ->
                        ExecutionContext.Static(methodId)
                    }
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
                    executionContextFor = { methodId: MethodId ->
                        ExecutionContext.Static(methodId)
                    }
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
        val descriptors: MutableList<MethodDescriptor> =
            descriptorsByClass.getOrPut(clazz) { mutableListOf() }

        val kotlinFunctionsByJavaMethod: Map<Method, KFunction<*>> =
            collectHierarchyFunctions(clazz.kotlin, inheritanceLevel)
                .mapNotNull { function: KFunction<*> ->
                    function.javaMethod?.let { javaMethod: Method ->
                        javaMethod to function
                    }
                }
                .toMap()

        for (javaMethod: Method in collectHierarchyMethods(clazz, inheritanceLevel)) {
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
            require(Modifier.isStatic(javaMethod.modifiers)) {
                "Method ${methodId.value} must be static"
            }
        } else {
            require(!Modifier.isStatic(javaMethod.modifiers)) {
                "Method ${methodId.value} must be an instance method"
            }
        }

        val descriptors: MutableList<MethodDescriptor> =
            descriptorsByClass.getOrPut(clazz) { mutableListOf() }

        if (!entriesById.containsKey(methodId)) {
            val descriptor: MethodDescriptor =
                resolved.kotlinFunction?.let { kotlinFunction: KFunction<*> ->
                    MethodDescriptor.from(kotlinFunction, javaMethod, methodId)
                } ?: run {
                    val parsed: ParsedMethodId = ParsedMethodId.parse(methodId)
                    MethodDescriptor.from(javaMethod, methodId, parsed.methodName)
                }

            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(
                descriptor = descriptor,
                javaMethod = javaMethod
            )
        }

        val executionContext: ExecutionContext = executionContextFor(methodId)
        executionContextsById[executionContext.executionId] = executionContext
    }

    private fun resolveMethod(
        clazz: Class<*>,
        methodId: MethodId
    ): ResolvedMethod {
        val resolvedJavaMethod: Method? = resolveJavaMethod(clazz, methodId)
        if (resolvedJavaMethod != null) {
            val kotlinFunction: KFunction<*>? =
                collectHierarchyFunctions(clazz.kotlin, inheritanceLevel)
                    .firstOrNull { function: KFunction<*> ->
                        function.javaMethod == resolvedJavaMethod
                    }

            return ResolvedMethod(
                kotlinFunction = kotlinFunction,
                javaMethod = resolvedJavaMethod
            )
        }

        val resolvedKotlinMethod: ResolvedMethod? = resolveKotlinMethod(clazz.kotlin, methodId)
        if (resolvedKotlinMethod != null) {
            return resolvedKotlinMethod
        }

        throwMethodNotFound(methodId)
    }

    private fun resolveJavaMethod(
        clazz: Class<*>,
        methodId: MethodId
    ): Method? =
        collectHierarchyMethods(clazz, inheritanceLevel)
            .firstOrNull { javaMethod: Method -> MethodId.from(javaMethod) == methodId }

    private fun resolveKotlinMethod(
        kClass: KClass<*>,
        methodId: MethodId
    ): ResolvedMethod? {
        val parsed: ParsedMethodId = ParsedMethodId.parse(methodId)

        if (parsed.declaringClassName != kClass.java.name) {
            return null
        }

        val kotlinFunction: KFunction<*> =
            collectHierarchyFunctions(kClass, inheritanceLevel)
                .firstOrNull { function: KFunction<*> ->
                    function.name == parsed.methodName &&
                            valueParameterTypeNames(function) == parsed.parameterTypeNames &&
                            function.javaMethod != null
                }
                ?: return null

        return ResolvedMethod(
            kotlinFunction = kotlinFunction,
            javaMethod = kotlinFunction.javaMethod!!
        )
    }

    private fun collectHierarchyFunctions(
        kClass: KClass<*>,
        level: InheritanceLevel
    ): List<KFunction<*>> =
        generateSequence(kClass) { current: KClass<*> ->
            current.java.superclass?.kotlin
        }
            .take(level.depth + 1)
            .flatMap { current: KClass<*> ->
                current.members
                    .filterIsInstance<KFunction<*>>()
                    .asSequence()
            }
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
            .toList()

    private fun collectHierarchyMethods(
        clazz: Class<*>,
        level: InheritanceLevel
    ): List<Method> =
        generateSequence(clazz) { current: Class<*> ->
            current.superclass
        }
            .take(level.depth + 1)
            .filter { current: Class<*> -> current != Any::class.java }
            .flatMap { current: Class<*> ->
                current.declaredMethods.asSequence()
            }
            .filter { javaMethod: Method ->
                Modifier.isPublic(javaMethod.modifiers) &&
                        !javaMethod.isSynthetic &&
                        !javaMethod.isBridge
            }
            .distinctBy { javaMethod: Method -> MethodId.from(javaMethod) }
            .toList()

    private fun valueParameterTypeNames(
        function: KFunction<*>
    ): List<String> =
        function.parameters
            .filter { parameter: KParameter -> parameter.kind == KParameter.Kind.VALUE }
            .map { parameter: KParameter ->
                val classifier: KClass<*> = parameter.type.classifier as KClass<*>
                classifier.java.name
            }

    private fun throwMethodNotFound(methodId: MethodId): Nothing =
        throw MethodNotFoundException(
            methodId = methodId,
            available = entriesById.keys.map { id: MethodId -> id.toString() }
        )
}