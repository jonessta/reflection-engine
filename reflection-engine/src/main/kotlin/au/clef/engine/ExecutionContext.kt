package au.clef.engine

import au.clef.engine.model.MethodDescriptor
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ExecutionId(private val value: String) {
    override fun toString(): String = value
}

sealed class ExecutionContext {

    abstract val executionId: ExecutionId
    abstract val descriptor: MethodDescriptor

    data class Static(override val descriptor: MethodDescriptor) : ExecutionContext() {

        override val executionId: ExecutionId = ExecutionId("static:${descriptor.id}")
    }

    /**
     * @param instanceId Descriptive identifier for UI. Not used for runtime lookup during invocation.
     */
    data class Instance(
        val instanceId: String,
        val instance: Any,
        override val descriptor: MethodDescriptor
    ) : ExecutionContext() {

        override val executionId: ExecutionId = ExecutionId("instance:$instanceId:${descriptor.id}")
    }
}
