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
        private val regex = Regex("""^([\w$.]+)#(\w+)\((.*)\)$""")

        fun parse(methodId: MethodId): ParsedMethodId {
            val match = regex.matchEntire(methodId.toString())
                ?: throw IllegalMethodIdException("Expected <class>#<method>(<paramTypes>)")

            val (className, methodName, params) = match.destructured

            val parameterTypeNames =
                if (params.isBlank()) emptyList()
                else params.split(",")

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
        methodSources.map { it.declaringClass.java }.distinct()

    override val knownClasses: List<Class<*>> =
        (methodSources.map { it.declaringClass } + methodSupportingTypes)
            .distinct()
            .map { it.java }

    init {
        require(methodSources.isNotEmpty()) { "methodSources must not be empty" }
        methodSources.forEach(::registerMethodSource)
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
        entriesById.values.map { it.descriptor }

    fun allExecutionContexts(): List<ExecutionContext> =
        executionContextsById.values.toList()

    private fun registerMethodSource(source: MethodSource) {
        val clazz = source.declaringClass.java

        when (source) {
            is MethodSource.StaticClass -> {
                registerMethods(
                    clazz = clazz,
                    requireStatic = true,
                    executionContextFor = { methodId ->
                        ExecutionContext.Static(methodId)
                    }
                )
            }

            is MethodSource.Instance -> {
                registerMethods(
                    clazz = clazz,
                    requireStatic = false,
                    executionContextFor = { methodId ->
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
                    executionContextFor = { methodId ->
                        ExecutionContext.Static(methodId)
                    }
                )
            }

            is MethodSource.InstanceMethod -> {
                registerSingleMethod(
                    clazz = clazz,
                    methodId = source.methodId,
                    requireStatic = false,
                    executionContextFor = { methodId ->
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
        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }

        for (javaMethod in collectHierarchyMethods(clazz, inheritanceLevel)) {
            val isStaticMethod = Modifier.isStatic(javaMethod.modifiers)
            if (isStaticMethod != requireStatic) {
                continue
            }

            val methodId = MethodId.from(javaMethod)
            if (entriesById.containsKey(methodId)) {
                continue
            }

            val descriptor = MethodDescriptor.from(javaMethod)

            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(
                descriptor = descriptor,
                javaMethod = javaMethod
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
        val javaMethod = resolved.javaMethod

        if (requireStatic) {
            require(Modifier.isStatic(javaMethod.modifiers)) {
                "Method ${methodId.value} must be static"
            }
        } else {
            require(!Modifier.isStatic(javaMethod.modifiers)) {
                "Method ${methodId.value} must be an instance method"
            }
        }

        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }

        if (!entriesById.containsKey(methodId)) {
            val descriptor =
                resolved.kotlinFunction?.let { kotlinFunction ->
                    MethodDescriptor.from(kotlinFunction, javaMethod, methodId)
                } ?: run {
                    val parsed = ParsedMethodId.parse(methodId)
                    MethodDescriptor.from(javaMethod, methodId, parsed.methodName)
                }

            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(
                descriptor = descriptor,
                javaMethod = javaMethod
            )
        }

        val executionContext = executionContextFor(methodId)
        executionContextsById[executionContext.executionId] = executionContext
    }

    private fun resolveMethod(
        clazz: Class<*>,
        methodId: MethodId
    ): ResolvedMethod {
        val resolvedJavaMethod = resolveJavaMethod(clazz, methodId)
        if (resolvedJavaMethod != null) {
            return ResolvedMethod(
                kotlinFunction = null,
                javaMethod = resolvedJavaMethod
            )
        }

        val resolvedKotlinMethod = resolveKotlinMethod(clazz.kotlin, methodId)
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
            .firstOrNull { MethodId.from(it) == methodId }

    private fun resolveKotlinMethod(
        kClass: KClass<*>,
        methodId: MethodId
    ): ResolvedMethod? {
        val parsed = ParsedMethodId.parse(methodId)

        if (parsed.declaringClassName != kClass.java.name) {
            return null
        }

        val kotlinFunction = collectHierarchyFunctions(kClass, inheritanceLevel)
            .firstOrNull { function ->
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
        generateSequence(kClass) { current ->
            current.java.superclass?.kotlin
        }
            .take(level.depth + 1)
            .flatMap { current ->
                current.members
                    .filterIsInstance<KFunction<*>>()
                    .asSequence()
            }
            .filter { function ->
                val javaMethod = function.javaMethod
                javaMethod != null &&
                        Modifier.isPublic(javaMethod.modifiers) &&
                        javaMethod.declaringClass != Any::class.java &&
                        !javaMethod.isSynthetic &&
                        !javaMethod.isBridge
            }
            .distinctBy { function ->
                "${function.name}(${valueParameterTypeNames(function).joinToString(",")})"
            }
            .toList()

    private fun collectHierarchyMethods(
        clazz: Class<*>,
        level: InheritanceLevel
    ): List<Method> =
        generateSequence(clazz) { current ->
            current.superclass
        }
            .take(level.depth + 1)
            .filter { it != Any::class.java }
            .flatMap { current ->
                current.declaredMethods.asSequence()
            }
            .filter { javaMethod ->
                Modifier.isPublic(javaMethod.modifiers) &&
                        !javaMethod.isSynthetic &&
                        !javaMethod.isBridge
            }
            .distinctBy { MethodId.from(it) }
            .toList()

    private fun valueParameterTypeNames(
        function: KFunction<*>
    ): List<String> =
        function.parameters
            .filter { it.kind == KParameter.Kind.VALUE }
            .map { parameter ->
                val classifier = parameter.type.classifier as KClass<*>
                classifier.java.name
            }

    private fun throwMethodNotFound(methodId: MethodId): Nothing =
        throw MethodNotFoundException(
            methodId = methodId,
            available = entriesById.keys.map { it.toString() }
        )
}