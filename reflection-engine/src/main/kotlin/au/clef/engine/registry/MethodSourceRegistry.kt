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
    val method: Method
)

private data class ResolvedMethod(
    val function: KFunction<*>?,
    val method: Method
)

private data class ParsedMethodId(
    val declaringClassName: String,
    val methodName: String,
    val parameterTypeNames: List<String>
) {
    companion object {
        private val METHOD_ID_OUTER_REGEX = Regex(
            """^([A-Za-z_][A-Za-z0-9_$.]*)#([A-Za-z_][A-Za-z0-9_$]*)\((.*)\)$"""
        )

        fun parse(methodId: MethodId): ParsedMethodId {
            val match = METHOD_ID_OUTER_REGEX.matchEntire(methodId.toString())
                ?: throw IllegalMethodIdException("expected <class>#<method>(<paramTypes>)")

            val declaringClassName = match.groupValues[1]
            val methodName = match.groupValues[2]
            val paramsPart = match.groupValues[3]

            val parameterTypeNames =
                if (paramsPart.isBlank()) emptyList()
                else paramsPart.split(",")

            return ParsedMethodId(
                declaringClassName = declaringClassName,
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
        methodSources.map { it.declaringClass.java }.distinct()

    override val knownClasses: List<Class<*>> =
        (methodSources.map(MethodSource::declaringClass) + methodSupportingTypes)
            .distinct()
            .map { it.java }

    init {
        require(methodSources.isNotEmpty()) { "methodSources must not be empty" }
        methodSources.forEach(::registerMethodSource)
    }

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        descriptorsByClass[clazz]?.toList()
            ?: throw IllegalArgumentException("Class not registered: ${clazz.name}")

    fun descriptor(id: MethodId): MethodDescriptor =
        entriesById[id]?.descriptor
            ?: throw MethodNotFoundException(
                methodId = id,
                available = entriesById.keys.map(MethodId::toString)
            )

    fun method(id: MethodId): Method =
        entriesById[id]?.method
            ?: throw MethodNotFoundException(
                methodId = id,
                available = entriesById.keys.map(MethodId::toString)
            )

    fun executionContext(executionId: ExecutionId): ExecutionContext =
        executionContextsById[executionId]
            ?: throw IllegalArgumentException("Unknown executionId: $executionId")

    fun allDescriptors(): List<MethodDescriptor> =
        entriesById.values.map { it.descriptor }

    fun allExecutionContexts(): List<ExecutionContext> =
        executionContextsById.values.toList()

    private fun registerMethodSource(methodSource: MethodSource) {
        val clazz = methodSource.declaringClass.java

        when (methodSource) {
            is MethodSource.Instance -> registerMethods(
                clazz = clazz,
                requireStatic = false,
                executionContextFor = { methodId ->
                    ExecutionContext.Instance(
                        instance = methodSource.instance,
                        instanceDescription = methodSource.instanceDescription,
                        methodId = methodId
                    )
                }
            )

            is MethodSource.InstanceMethod -> registerSingleMethod(
                clazz = clazz,
                methodId = methodSource.methodId,
                requireStatic = false,
                executionContextFor = { methodId ->
                    ExecutionContext.Instance(
                        instance = methodSource.instance,
                        instanceDescription = methodSource.instanceDescription,
                        methodId = methodId
                    )
                }
            )

            is MethodSource.StaticClass -> registerMethods(
                clazz = clazz,
                requireStatic = true,
                executionContextFor = { methodId ->
                    ExecutionContext.Static(methodId)
                }
            )

            is MethodSource.StaticMethod -> registerSingleMethod(
                clazz = clazz,
                methodId = methodSource.methodId,
                requireStatic = true,
                executionContextFor = { methodId ->
                    ExecutionContext.Static(methodId)
                }
            )
        }
    }

    private fun registerMethods(
        clazz: Class<*>,
        requireStatic: Boolean,
        executionContextFor: (MethodId) -> ExecutionContext
    ) {
        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }

        for (method in collectHierarchyMethods(clazz, inheritanceLevel)) {
            if (Modifier.isStatic(method.modifiers) != requireStatic) continue

            val methodId = MethodId.from(method)
            if (entriesById.containsKey(methodId)) continue

            val descriptor = MethodDescriptor.from(method)

            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(
                descriptor = descriptor,
                method = method
            )

            val executionContext = executionContextFor(methodId)
            executionContextsById[executionContext.executionId] = executionContext
        }
    }

    private fun registerSingleMethod(
        clazz: Class<*>,
        methodId: MethodId,
        requireStatic: Boolean,
        executionContextFor: (MethodId) -> ExecutionContext
    ) {
        val resolved = resolveMethod(clazz, methodId)
        val method = resolved.method

        if (requireStatic) {
            require(Modifier.isStatic(method.modifiers)) {
                "Method ${methodId.value} must be static"
            }
        } else {
            require(!Modifier.isStatic(method.modifiers)) {
                "Method ${methodId.value} must be an instance method"
            }
        }

        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }

        if (!entriesById.containsKey(methodId)) {
            val descriptor =
                if (resolved.function != null) {
                    MethodDescriptor.from(
                        function = resolved.function,
                        method = method,
                        id = methodId
                    )
                } else {
                    val parsed = ParsedMethodId.parse(methodId)
                    MethodDescriptor.from(
                        method = method,
                        id = methodId,
                        logicalMethodName  = parsed.methodName
                    )
                }

            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(
                descriptor = descriptor,
                method = method
            )
        }

        val executionContext = executionContextFor(methodId)
        executionContextsById[executionContext.executionId] = executionContext
    }

    private fun resolveMethod(clazz: Class<*>, methodId: MethodId): ResolvedMethod {
        resolveJavaMethod(clazz, methodId)?.let { return ResolvedMethod(function = null, method = it) }
        resolveKotlinMethod(clazz.kotlin, methodId)?.let { return it }

        val availableJava = collectHierarchyMethods(clazz, inheritanceLevel)
            .map { MethodId.from(it).toString() }

        throw MethodNotFoundException(
            methodId = methodId,
            available = availableJava
        )
    }

    private fun resolveKotlinMethod(
        declaringClass: KClass<*>,
        methodId: MethodId
    ): ResolvedMethod? {
        val parsed = ParsedMethodId.parse(methodId)

        if (parsed.declaringClassName != declaringClass.java.name) {
            return null
        }

        val candidates = collectHierarchyFunctions(declaringClass, inheritanceLevel)
            .filter { function ->
                function.name == parsed.methodName &&
                        valueParameterTypeNames(function) == parsed.parameterTypeNames &&
                        function.javaMethod != null
            }

        val function = candidates.singleOrNull() ?: return null
        return ResolvedMethod(
            function = function,
            method = requireNotNull(function.javaMethod)
        )
    }

    private fun resolveJavaMethod(clazz: Class<*>, methodId: MethodId): Method? {
        val methods = collectHierarchyMethods(clazz, inheritanceLevel)
        return methods.firstOrNull { MethodId.from(it) == methodId }
    }

    // todo can this be simpler
    private fun collectHierarchyFunctions(
        declaringClass: KClass<*>,
        inheritanceLevel: InheritanceLevel
    ): List<KFunction<*>> {
        val functions = mutableListOf<KFunction<*>>()
        var current: KClass<*>? = declaringClass
        var depth = 0

        while (current != null && depth <= inheritanceLevel.depth) {
            functions += current.members.filterIsInstance<KFunction<*>>()
            current = current.java.superclass?.kotlin
            depth++
        }

        return functions
            .filter { function ->
                val javaMethod = function.javaMethod
                javaMethod != null &&
                        Modifier.isPublic(javaMethod.modifiers) &&
                        javaMethod.declaringClass != Any::class.java &&
                        !javaMethod.isSynthetic &&
                        !javaMethod.isBridge
            }
            .distinctBy { function ->
                buildString {
                    append(function.name)
                    append("(")
                    append(valueParameterTypeNames(function).joinToString(","))
                    append(")")
                }
            }
    }

    private fun valueParameterTypeNames(function: KFunction<*>): List<String> =
        function.parameters
            .filter { it.kind == KParameter.Kind.VALUE }
            .map { parameter ->
                val classifier = parameter.type.classifier as? KClass<*>
                    ?: throw IllegalMethodIdException(
                        "Unsupported parameter type in function '${function.name}'"
                    )
                classifier.java.name
            }

    private fun collectHierarchyMethods(
        clazz: Class<*>,
        inheritanceLevel: InheritanceLevel
    ): List<Method> {
        val methods = mutableListOf<Method>()
        var current: Class<*>? = clazz
        var depth = 0

        while (current != null && depth <= inheritanceLevel.depth) {
            methods += current.declaredMethods
            current = current.superclass
            depth++
        }

        return methods
            .filter { Modifier.isPublic(it.modifiers) }
            .filterNot { it.declaringClass == Any::class.java }
            .filterNot(Method::isSynthetic)
            .filterNot(Method::isBridge)
            .distinctBy(MethodId::from)
    }
}