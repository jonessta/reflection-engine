package au.clef.engine.model

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

class MethodDescriptor(
    val id: MethodId,
    val reflectedName: String,
    val displayName: String? = null,
    val parameters: List<ParamDescriptor>,
    val returnType: Class<*>,
    val isStatic: Boolean
) {

    companion object {

        fun from(
            javaMethod: Method,
            displayName: String? = null
        ): MethodDescriptor =
            create(
                id = MethodId.from(javaMethod),
                name = javaMethod.name,
                displayName = displayName,
                parameters = buildJavaParamDescriptors(javaMethod),
                returnType = javaMethod.returnType,
                isStatic = Modifier.isStatic(javaMethod.modifiers)
            )

        fun from(
            javaMethod: Method,
            id: MethodId,
            logicalMethodName: String,
            displayName: String? = null
        ): MethodDescriptor =
            create(
                id = id,
                // todo change ?? reflectionName is legacy
                name = logicalMethodName,
                displayName = displayName,
                parameters = buildJavaParamDescriptors(javaMethod),
                returnType = javaMethod.returnType,
                isStatic = Modifier.isStatic(javaMethod.modifiers)
            )

        fun from(
            kotlinFunction: KFunction<*>,
            javaMethod: Method,
            id: MethodId,
            displayName: String? = null
        ): MethodDescriptor =
            create(
                id = id,
                name = kotlinFunction.name,
                displayName = displayName,
                parameters = buildKotlinParamDescriptors(kotlinFunction, javaMethod),
                returnType = javaMethod.returnType,
                isStatic = Modifier.isStatic(javaMethod.modifiers)
            )

        private fun create(
            id: MethodId,
            name: String,
            displayName: String?,
            parameters: List<ParamDescriptor>,
            returnType: Class<*>,
            isStatic: Boolean
        ): MethodDescriptor =
            MethodDescriptor(
                id = id,
                reflectedName = name,
                displayName = displayName,
                parameters = parameters,
                returnType = returnType,
                isStatic = isStatic
            )
    }

    fun withMetadata(
        displayName: String? = this.displayName,
        parameters: List<ParamDescriptor> = this.parameters
    ): MethodDescriptor =
        MethodDescriptor(
            id = id,
            reflectedName = reflectedName,
            displayName = displayName,
            parameters = parameters,
            returnType = returnType,
            isStatic = isStatic
        )

    override fun equals(other: Any?): Boolean =
        other is MethodDescriptor && id == other.id

    override fun hashCode(): Int =
        id.hashCode()

    override fun toString(): String =
        "MethodDescriptor(id=$id, reflectedName=$reflectedName, displayName=$displayName, parameters=$parameters)"
}

data class ParamDescriptor(
    val index: Int,
    val logicalType: Class<*>,
    val runtimeType: Class<*>,
    val reflectedName: String,
    val name: String,
    val nullable: Boolean
)

private fun buildJavaParamDescriptors(javaMethod: Method): List<ParamDescriptor> =
    javaMethod.parameters.mapIndexed { index, parameter ->
        ParamDescriptor(
            index = index,
            logicalType = parameter.type,
            runtimeType = parameter.type,
            // todo do I need reflectedName?
            reflectedName = parameter.name,
            name = parameter.name,
            nullable = !parameter.type.isPrimitive
        )
    }

private fun buildKotlinParamDescriptors(
    kotlinFunction: KFunction<*>,
    javaMethod: Method
): List<ParamDescriptor> {
    val valueParameters: List<KParameter> =
        kotlinFunction.parameters.filter { it.kind == KParameter.Kind.VALUE }

    val runtimeParameterTypes: Array<Class<*>> = javaMethod.parameterTypes

    return valueParameters.mapIndexed { index, parameter ->
        val classifier = parameter.type.classifier as? KClass<*>
            ?: error("Unsupported parameter type in function '${kotlinFunction.name}'")

        val parameterName = parameter.name ?: "arg$index"

        ParamDescriptor(
            index = index,
            logicalType = classifier.java,
            runtimeType = runtimeParameterTypes[index],
            reflectedName = parameterName,
            name = parameterName,
            nullable = parameter.type.isMarkedNullable
        )
    }
}