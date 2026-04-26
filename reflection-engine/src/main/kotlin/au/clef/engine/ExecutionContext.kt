package au.clef.engine

import au.clef.engine.model.MethodId
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ExecutionId(private val value: String) {
    override fun toString(): String = value
}

sealed class ExecutionContext {

    abstract val executionId: ExecutionId
    abstract val methodId: MethodId

    data class Static(
        override val methodId: MethodId
    ) : ExecutionContext() {

        override val executionId: ExecutionId = ExecutionId("static:${methodId.value}")
    }

    /**
     * @param instanceId Descriptive identifier for UI. Not used for runtime lookup during invocation.
     */
    data class Instance(
        val instanceId: String,
        val instance: Any,
        override val methodId: MethodId
    ) : ExecutionContext() {

        override val executionId: ExecutionId = ExecutionId("instance:$instanceId:${methodId.value}")
    }
}
