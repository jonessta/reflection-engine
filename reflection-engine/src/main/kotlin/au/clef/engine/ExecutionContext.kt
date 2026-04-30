package au.clef.engine

import au.clef.engine.model.MethodId
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ExecutionId(val value: String) {
    override fun toString(): String = value
}

sealed class ExecutionContext(
    open val methodId: MethodId
) {
    abstract val executionId: ExecutionId

    data class Static(
        override val methodId: MethodId
    ) : ExecutionContext(methodId) {
        override val executionId: ExecutionId =
            ExecutionId("static:${methodId}")
    }

    data class Instance(
        val instanceDescription: String,
        val instance: Any,
        override val methodId: MethodId
    ) : ExecutionContext(methodId) {
        override val executionId: ExecutionId =
            ExecutionId("instance:${UUID.randomUUID()}:${methodId}")
    }
}