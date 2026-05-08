package au.clef.api

import au.clef.engine.MethodSource
import au.clef.engine.ReflectionConfig
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type

class MethodTypeRoots(private val reflectionConfig: ReflectionConfig) {

    fun all(): List<Type> = reflectionConfig.methodSources
        .flatMap { source: MethodSource ->
            methodsFor(source).flatMap { method: Method ->
                method.genericParameterTypes.toList() + method.genericReturnType
            }
        }

    private fun methodsFor(source: MethodSource): List<Method> =
        when (source) {
            is MethodSource.StaticClass -> source.declaringClass.java.declaredMethods
                .filter { method: Method -> Modifier.isStatic(method.modifiers) }

            is MethodSource.Instance -> source.instance::class.java.declaredMethods
                .filter { method: Method -> !Modifier.isStatic(method.modifiers) }

            is MethodSource.StaticMethod -> listOf(source.methodId.resolve(source.declaringClass.java))

            is MethodSource.InstanceMethod ->
                listOf(source.methodId.resolve(source.instance::class.java))
        }
}