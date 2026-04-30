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

        // todo why not make the from just constructors?
        fun from(method: Method, displayName: String? = null): MethodDescriptor =
            MethodDescriptor(
                id = MethodId.from(method),
                reflectedName = method.name,
                displayName = displayName,
                parameters = buildJavaParamDescriptors(method),
                returnType = method.returnType,
                isStatic = Modifier.isStatic(method.modifiers)
            )

        fun from(
            method: Method,
            id: MethodId,
            logicalMethodName: String,
            displayName: String? = null
        ): MethodDescriptor =
            MethodDescriptor(
                id = id,
                reflectedName = logicalMethodName,
                displayName = displayName,
                parameters = buildJavaParamDescriptors(method),
                returnType = method.returnType,
                isStatic = Modifier.isStatic(method.modifiers)
            )

        fun from(
            function: KFunction<*>,
            method: Method,
            id: MethodId,
            displayName: String? = null
        ): MethodDescriptor =
            MethodDescriptor(
                id = id,
                reflectedName = function.name,
                displayName = displayName,
                parameters = buildKotlinParamDescriptors(function, method),
                returnType = method.returnType,
                isStatic = Modifier.isStatic(method.modifiers)
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
    val label: String? = null,
    val nullable: Boolean
)

private fun buildJavaParamDescriptors(method: Method): List<ParamDescriptor> =
    method.parameters.mapIndexed { index, parameter ->
        ParamDescriptor(
            index = index,
            logicalType = parameter.type,
            runtimeType = parameter.type,
            reflectedName = parameter.name,
            name = parameter.name,
            label = null,
            nullable = !parameter.type.isPrimitive
        )
    }

private fun buildKotlinParamDescriptors(
    function: KFunction<*>,
    method: Method
): List<ParamDescriptor> {
    val valueParameters = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
    val runtimeParameterTypes = method.parameterTypes

    return valueParameters.mapIndexed { index, parameter ->
        val classifier = parameter.type.classifier as? KClass<*>
            ?: error("Unsupported parameter type in function '${function.name}'")

        val parameterName = parameter.name ?: "arg$index"

        ParamDescriptor(
            index = index,
            logicalType = classifier.java,
            runtimeType = runtimeParameterTypes[index],
            reflectedName = parameterName,
            name = parameterName,
            label = null,
            nullable = parameter.type.isMarkedNullable
        )
    }
}