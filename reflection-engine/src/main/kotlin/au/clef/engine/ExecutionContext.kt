package au.clef.engine

import au.clef.engine.model.MethodId

@JvmInline
value class ExecutionId(val value: String) {
    override fun toString(): String = value
}

sealed class ExecutionContext {
    abstract val executionId: ExecutionId
    abstract val methodId: MethodId

    data class Static(
        override val methodId: MethodId
    ) : ExecutionContext() {
        override val executionId: ExecutionId =
            ExecutionId("static:${methodId.value}")
    }

    data class Instance(
        /**
         * Descriptive identifier for UI and execution id construction.
         * Not used for runtime lookup during invocation.
         */
        val instanceId: String,
        val instance: Any,
        override val methodId: MethodId
    ) : ExecutionContext() {
        override val executionId: ExecutionId =
            ExecutionId("instance:$instanceId:${methodId.value}")
    }
}
