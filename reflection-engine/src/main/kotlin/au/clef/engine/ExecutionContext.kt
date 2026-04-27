package au.clef.engine

import au.clef.engine.model.MethodDescriptor
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@JvmInline
value class ExecutionId(val value: String) {
    override fun toString(): String = value
}

sealed class ExecutionContext {

    abstract val executionId: ExecutionId
    abstract val descriptor: MethodDescriptor

    data class Static(override val descriptor: MethodDescriptor) : ExecutionContext() {
        override val executionId: ExecutionId = ExecutionId("static:${descriptor.id}")
    }

    data class Instance(
        val instanceDescription: String,
        val instance: Any,
        // todo do i need descriptor?
        override val descriptor: MethodDescriptor
    ) : ExecutionContext() {
        override val executionId: ExecutionId = ExecutionId("instance:${UUID.randomUUID()}:${descriptor.id}")
    }
}