package au.clef.engine.registry

import au.clef.engine.MethodNotFoundException
import au.clef.engine.model.InheritanceLevel
import au.clef.engine.model.MethodDescriptor
import au.clef.engine.model.MethodId
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class MethodRegistry(
    vararg classes: KClass<*>,
    private val inheritanceLevel: InheritanceLevel = InheritanceLevel.DeclaredOnly
) {

    private val descriptorsByClass: MutableMap<Class<*>, List<MethodDescriptor>> = ConcurrentHashMap()
    private val descriptorsById: MutableMap<MethodId, MethodDescriptor> = ConcurrentHashMap()

    init {
        require(classes.isNotEmpty()) { "Classes must not be empty" }
    }

    init {
        classes.forEach { addKClass(it) }
    }

    fun addKClass(clazz: KClass<*>) {
        addKClass(clazz.java)
    }

    fun addKClass(clazz: Class<*>) {
        if (descriptorsByClass.containsKey(clazz))
            return

        val descriptors: List<MethodDescriptor> = buildDescriptors(clazz, inheritanceLevel)
        descriptorsByClass[clazz] = descriptors
        descriptors.forEach { descriptor -> descriptorsById[descriptor.id] = descriptor }
    }

    private fun collectMethods(clazz: Class<*>, inheritanceLevel: InheritanceLevel): List<Method> =
        when (inheritanceLevel) {
            InheritanceLevel.DeclaredOnly ->
                clazz.declaredMethods.toList()

            InheritanceLevel.All -> {
                val methods: MutableList<Method> = mutableListOf()
                var current: Class<*>? = clazz
                while (current != null) {
                    methods += current.declaredMethods
                    current = current.superclass
                }
                methods.distinctBy { method: Method ->
                    MethodId.from(method)
                }
            }

            is InheritanceLevel.Depth -> {
                val methods: MutableList<Method> = mutableListOf()
                var current: Class<*>? = clazz
                var depth: Int = 0
                while (current != null && depth <= inheritanceLevel.value) {
                    methods += current.declaredMethods
                    current = current.superclass
                    depth++
                }
                methods.distinctBy { method: Method -> MethodId.from(method) }
            }
        }

    private fun buildDescriptors(clazz: Class<*>, inheritanceLevel: InheritanceLevel): List<MethodDescriptor> {
        val methods: List<Method> = collectMethods(clazz, inheritanceLevel)
        return methods.map { method: Method -> MethodDescriptor(method) }
    }

    fun addClassByName(className: String) {
        val clazz: Class<*> = try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("Class not found: $className", e)
        }
        addKClass(clazz)
    }

    fun descriptors(clazz: Class<*>): List<MethodDescriptor> =
        descriptorsByClass[clazz] ?: throw IllegalArgumentException("Class not registered: ${clazz.name}")

    fun descriptorsByKClass(clazz: KClass<*>): List<MethodDescriptor> = descriptors(clazz.java)

    fun findDescriptorById(id: MethodId): MethodDescriptor =
        descriptorsById[id] ?: throw MethodNotFoundException(
            methodId = id,
            available = descriptorsById.keys.map { it.toString() }
        )

    fun allDescriptors(): List<MethodDescriptor> = descriptorsById.values.toList()

    fun clear() {
        descriptorsByClass.clear()
        descriptorsById.clear()
    }
}