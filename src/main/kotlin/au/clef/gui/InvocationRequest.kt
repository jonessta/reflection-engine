package au.clef.gui

import au.clef.ExecutionContext
import au.clef.MethodDescriptor

data class InvocationRequest(
    val descriptor: MethodDescriptor,
    val instance: Any? = null,
    val values: List<Any?>
) {

    fun execute(request: InvocationRequest): Any? {
        require(request.descriptor.isStatic || request.instance != null) {
            "Instance required for method ${request.descriptor.name}"
        }
        val context = ExecutionContext(request.instance)
        return request.descriptor.invoke(context, request.values)
    }

    fun invokeDescriptor(
        descriptor: MethodDescriptor,
        instance: Any? = null,
        args: List<Any?>
    ): Any? {
        require(descriptor.isStatic || instance != null) {
            "Instance required for method ${descriptor.name}"
        }
        return descriptor.invoke(ExecutionContext(instance), args)
    }
}
/*
val result = app.invokeDescriptor(
    descriptor = selectedDescriptor,
    instance = selectedInstance,
    args = formValues
)
 */