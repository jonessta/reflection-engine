package au.clef.engine.registry

import au.clef.engine.MethodNotFoundException
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class ReflectionRegistry(
    targetClasses: List<KClass<*>>,
    supportingClasses: List<KClass<*>> = emptyList(),
    private val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) : ReflectionClasses {

    private val descriptorsByClass: MutableMap<Class<*>, List<MethodDescriptor>> = ConcurrentHashMap()
    private val descriptorsById: MutableMap<MethodId, MethodDescriptor> = ConcurrentHashMap()
    private val methodsById: MutableMap<MethodId, Method> = ConcurrentHashMap()

    override val targetClasses: List<Class<*>> = targetClasses.map { it.java }

    override val classes: List<Class<*>> = (targetClasses + supportingClasses).map { it.java }.distinct()

    init {
        require(targetClasses.isNotEmpty()) { "target classes must not be empty" }
        targetClasses.forEach { registerClass(it.java) }
    }

    fun registerClass(clazz: KClass<*>) = registerClass(clazz.java)

    fun registerClass(clazz: Class<*>) {
        if (descriptorsByClass.containsKey(clazz))
            return

        val methods: List<Method> = collectMethods(clazz, inheritanceLevel)
        val descriptors: MutableList<MethodDescriptor> = mutableListOf()
        for (method: Method in methods) {
            val methodId: MethodId = MethodId.from(method)
            val descriptor: MethodDescriptor = MethodDescriptor.from(method, methodId)
            descriptors += descriptor
            methodsById[methodId] = method
            descriptorsById[methodId] = descriptor
        }
        descriptorsByClass[clazz] = descriptors
    }

    private fun collectMethods(clazz: Class<*>, inheritanceLevel: InheritanceLevel): List<Method> =
        when (inheritanceLevel) {
            InheritanceLevel.DeclaredOnly -> clazz.declaredMethods.toList()

            // todo ALL and Depth are very similar
            InheritanceLevel.All -> {
                val methods: MutableList<Method> = mutableListOf()
                var current: Class<*>? = clazz
                while (current != null) {
                    methods += current.declaredMethods
                    current = current.superclass
                }
                methods.distinctBy(MethodId::from)
            }

            is InheritanceLevel.Depth -> {
                val methods: MutableList<Method> = mutableListOf()
                var current: Class<*>? = clazz
                var depth = 0
                while (current != null && depth <= inheritanceLevel.value) {
                    methods += current.declaredMethods
                    current = current.superclass
                    depth++
                }
                methods.distinctBy(MethodId::from)
            }
        }

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        descriptorsByClass[clazz]
            ?: throw IllegalArgumentException("Class not registered: ${clazz.name}")

    fun descriptors(clazz: KClass<*>): List<MethodDescriptor> = descriptors(clazz.java)

    fun descriptor(id: MethodId): MethodDescriptor =
        descriptorsById[id]
            ?: throw MethodNotFoundException(methodId = id, available = descriptorsById.keys.map(MethodId::toString))

    fun method(id: MethodId): Method =
        methodsById[id]
            ?: throw MethodNotFoundException(methodId = id, available = methodsById.keys.map(MethodId::toString))

    fun allDescriptors(): List<MethodDescriptor> = descriptorsById.values.toList()
}