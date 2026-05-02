package au.clef.engine

import au.clef.engine.model.MethodId
import java.util.*

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
        val instance: Any,
        val instanceDescription: String,
        override val methodId: MethodId
    ) : ExecutionContext(methodId) {
        override val executionId: ExecutionId =
            ExecutionId("instance:${UUID.randomUUID()}:${methodId}")
    }
}