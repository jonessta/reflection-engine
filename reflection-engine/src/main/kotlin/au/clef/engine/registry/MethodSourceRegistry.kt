package au.clef.engine.registry

import au.clef.engine.ExecutionContext
import au.clef.engine.ExecutionId
import au.clef.engine.MethodNotFoundException
import au.clef.engine.MethodSource
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

private data class RegistryEntry(val descriptor: MethodDescriptor, val method: Method)

class MethodSourceRegistry(
    methodSources: Collection<MethodSource>,
    methodSupportingTypes: Collection<KClass<*>> = emptyList(),
    private val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) : MethodSourceTypes {

    private val descriptorsByClass: MutableMap<Class<*>, MutableList<MethodDescriptor>> = ConcurrentHashMap()
    private val entriesById: MutableMap<MethodId, RegistryEntry> = ConcurrentHashMap()
    private val executionContextsById: MutableMap<ExecutionId, ExecutionContext> = ConcurrentHashMap()

    override val declaringClasses: List<Class<*>> = methodSources.map { it.declaringClass.java }.distinct()

    override val knownClasses: List<Class<*>> =
        (methodSources.map { methodSource: MethodSource -> methodSource.declaringClass } + methodSupportingTypes)
            .distinct()
            .map { it.java }

    init {
        require(methodSources.isNotEmpty()) { "methodSources must not be empty" }
        methodSources.forEach(::registerMethodSource)
    }

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        descriptorsByClass[clazz]?.toList() ?: throw IllegalArgumentException("Class not registered: ${clazz.name}")

    fun descriptor(id: MethodId): MethodDescriptor =
        entriesById[id]?.descriptor
            ?: throw MethodNotFoundException(methodId = id, available = entriesById.keys.map(MethodId::toString))

    fun method(id: MethodId): Method =
        entriesById[id]?.method
            ?: throw MethodNotFoundException(methodId = id, available = entriesById.keys.map(MethodId::toString))

    fun executionContext(executionId: ExecutionId): ExecutionContext =
        executionContextsById[executionId] ?: throw IllegalArgumentException("Unknown executionId: $executionId")

    fun allDescriptors(): List<MethodDescriptor> = entriesById.values.map { it.descriptor }

    fun allExecutionContexts(): List<ExecutionContext> = executionContextsById.values.toList()

    private fun registerMethodSource(methodSource: MethodSource) {
        val clazz = methodSource.declaringClass.java
        when (methodSource) {
            is MethodSource.Instance -> registerMethods(
                clazz = clazz,
                requireStatic = false,
                executionContextFor = { descriptor ->
                    ExecutionContext.Instance(
                        instance = methodSource.instance,
                        instanceDescription = methodSource.instanceDescription,
                        descriptor = descriptor
                    )
                }
            )

            is MethodSource.InstanceMethod -> registerSingleMethod(
                clazz = clazz,
                methodId = methodSource.methodId,
                requireStatic = false,
                executionContextFor = { descriptor ->
                    ExecutionContext.Instance(
                        instance = methodSource.instance,
                        instanceDescription = methodSource.instanceDescription,
                        descriptor = descriptor
                    )
                }
            )

            is MethodSource.StaticClass -> registerMethods(
                clazz = clazz,
                requireStatic = true,
                executionContextFor = { descriptor -> ExecutionContext.Static(descriptor) }
            )

            is MethodSource.StaticMethod -> registerSingleMethod(
                clazz = clazz,
                methodId = methodSource.methodId,
                requireStatic = true,
                executionContextFor = { descriptor -> ExecutionContext.Static(descriptor) }
            )
        }
    }

    private fun registerMethods(
        clazz: Class<*>,
        requireStatic: Boolean,
        executionContextFor: (MethodDescriptor) -> ExecutionContext
    ) {
        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }
        for (method in collectHierarchyMethods(clazz, inheritanceLevel)) {
            if (Modifier.isStatic(method.modifiers) != requireStatic) continue

            val descriptor = MethodDescriptor.from(method)
            val methodId = descriptor.id
            if (entriesById.containsKey(methodId)) continue

            descriptors += descriptor
            entriesById[methodId] = RegistryEntry(descriptor = descriptor, method = method)

            val executionContext = executionContextFor(descriptor)
            executionContextsById[executionContext.executionId] = executionContext
        }
    }

    private fun registerSingleMethod(
        clazz: Class<*>,
        methodId: MethodId,
        requireStatic: Boolean,
        executionContextFor: (MethodDescriptor) -> ExecutionContext
    ) {
        val method = resolveMethod(clazz, methodId)
        if (requireStatic) {
            require(Modifier.isStatic(method.modifiers)) { "Method ${methodId.value} must be static" }
        } else {
            require(!Modifier.isStatic(method.modifiers)) { "Method ${methodId.value} must be an instance method" }
        }

        val descriptors = descriptorsByClass.getOrPut(clazz) { mutableListOf() }
        val descriptor = entriesById[methodId]?.descriptor ?: MethodDescriptor.from(method).also {
            descriptors += it
            entriesById[it.id] = RegistryEntry(descriptor = it, method = method)
        }

        val executionContext = executionContextFor(descriptor)
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