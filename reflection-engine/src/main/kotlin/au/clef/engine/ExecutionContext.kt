package au.clef.engine

import au.clef.engine.model.MethodDescriptor
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@JvmInline
value class ExecutionId(val value: String) {
    override fun toString(): String = value
}

sealed class ExecutionContext(open val executionId: ExecutionId, open val descriptor: MethodDescriptor) {

    data class Static(override val descriptor: MethodDescriptor) : ExecutionContext(
        executionId = ExecutionId("static:${descriptor.id}"),
        descriptor = descriptor
    )

    data class Instance(
        val instanceDescription: String,
        val instance: Any,
        override val descriptor: MethodDescriptor
    ) : ExecutionContext(
        executionId = ExecutionId("instance:${UUID.randomUUID()}:${descriptor.id}"),
        descriptor = descriptor
    )
}