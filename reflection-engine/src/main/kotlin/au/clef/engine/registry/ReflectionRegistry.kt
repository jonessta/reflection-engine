package au.clef.engine.registry

import au.clef.engine.ExecutionContext
import au.clef.engine.ExecutionId
import au.clef.engine.MethodSource
import au.clef.engine.MethodNotFoundException
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

private data class RegistryEntry(val descriptor: MethodDescriptor, val method: Method)

class ReflectionRegistry(
    methodSources: Collection<MethodSource>,
    methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    private val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) : ReflectionTypes {

    private val descriptorsByClass: MutableMap<Class<*>, MutableList<MethodDescriptor>> = ConcurrentHashMap()
    private val entriesById: MutableMap<MethodId, RegistryEntry> = ConcurrentHashMap()
    private val executionContextsById: MutableMap<ExecutionId, ExecutionContext> = ConcurrentHashMap()

    override val declaringClasses: List<Class<*>> = methodSources.map { it.declaringClass.java }.distinct()

    override val classes: List<Class<*>> =
        (methodSources.map { methodSource: MethodSource -> methodSource.declaringClass } + methodSupportingTypes)
            .distinct()
            .map { it.java }

    init {
        require(methodSources.isNotEmpty()) { "methodSources must not be empty" }
        methodSources.forEach(::registerMethodSource)
    }

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        descriptorsByClass[clazz]?.toList()
            ?: throw IllegalArgumentException("Class not registered: ${clazz.name}")

    fun descriptors(clazz: KClass<*>): List<MethodDescriptor> = descriptors(clazz.java)

    fun descriptor(id: MethodId): MethodDescriptor =
        entriesById[id]?.descriptor
            ?: throw MethodNotFoundException(methodId = id, available = entriesById.keys.map(MethodId::toString))

    fun method(id: MethodId): Method =
        entriesById[id]?.method
            ?: throw MethodNotFoundException(methodId = id, available = entriesById.keys.map(MethodId::toString))

    fun executionContext(executionId: ExecutionId): ExecutionContext =
        executionContextsById[executionId]
            ?: throw IllegalArgumentException("Unknown executionId: ${executionId.value}")

    fun allDescriptors(): List<MethodDescriptor> = entriesById.values.map { it.descriptor }

    fun allExecutionContexts(): List<ExecutionContext> = executionContextsById.values.toList()

    private fun registerMethodSource(methodSource: MethodSource) {
        when (methodSource) {
            is MethodSource.Instance ->
                registerMethods(
                    clazz = methodSource.declaringClass.java,
                    predicate = { method -> !Modifier.isStatic(method.modifiers) },
                    executionContextFor = { methodId ->
                        ExecutionContext.Instance(methodSource.id, methodId)
                    }
                )

            is MethodSource.InstanceMethod ->
                registerSingleMethod(
                    clazz = methodSource.declaringClass.java,
                    methodId = methodSource.methodId,
                    requireStatic = false,
                    executionContext = ExecutionContext.Instance(methodSource.id, methodSource.methodId)
                )

            is MethodSource.StaticClass ->
                registerMethods(
                    clazz = methodSource.declaringClass.java,
                    predicate = { method -> Modifier.isStatic(method.modifiers) },
                    executionContextFor = { methodId ->
                        ExecutionContext.Static(methodId)
                    }
                )

            is MethodSource.StaticMethod ->
                registerSingleMethod(
                    clazz = methodSource.declaringClass.java,
                    methodId = methodSource.methodId,
                    requireStatic = true,
                    executionContext = ExecutionContext.Static(methodSource.methodId)
                )
        }
    }

    private fun registerMethods(
        clazz: Class<*>,
        predicate: (Method) -> Boolean,
        executionContextFor: (MethodId) -> ExecutionContext
    ) {
        val descriptors: MutableList<MethodDescriptor> = descriptorsByClass.getOrPut(clazz) { mutableListOf() }
        collectHierarchyMethods(clazz, inheritanceLevel)
            .filter(predicate)
            .forEach { method ->
                val methodId = MethodId.from(method)
                if (entriesById.containsKey(methodId))
                    return@forEach
                val descriptor: MethodDescriptor = MethodDescriptor.from(method)
                descriptors += descriptor
                entriesById[methodId] = RegistryEntry(descriptor = descriptor, method = method)
                val executionContext = executionContextFor(methodId)
                executionContextsById[executionContext.executionId] = executionContext
            }
    }

    private fun registerSingleMethod(
        clazz: Class<*>,
        methodId: MethodId,
        requireStatic: Boolean,
        executionContext: ExecutionContext
    ) {
        val method = resolveMethod(clazz, methodId)
        if (requireStatic) {
            require(Modifier.isStatic(method.modifiers)) { "Method ${methodId.value} must be static" }
        } else {
            require(!Modifier.isStatic(method.modifiers)) { "Method ${methodId.value} must be an instance method" }
        }

        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }
        if (!entriesById.containsKey(methodId)) {
            val descriptor = MethodDescriptor.from(method)
            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(descriptor = descriptor, method = method)
        }

        executionContextsById[executionContext.executionId] = executionContext
    }

    private fun resolveMethod(clazz: Class<*>, methodId: MethodId): Method {
        val methods = collectHierarchyMethods(clazz, inheritanceLevel)
        return methods.firstOrNull { MethodId.from(it) == methodId }
            ?: throw MethodNotFoundException(
                methodId = methodId,
                available = methods.map { MethodId.from(it).toString() }
            )
    }

    private fun collectHierarchyMethods(clazz: Class<*>, inheritanceLevel: InheritanceLevel): List<Method> {
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