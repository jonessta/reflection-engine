package au.clef.engine

import au.clef.engine.model.ExecutionId
import au.clef.engine.model.MethodId
import java.util.*

sealed class ExecutionContext(val methodId: MethodId, val sourceDescription: String? = null) {

    abstract val executionId: ExecutionId

    class Static(methodId: MethodId) : ExecutionContext(methodId) {

        override val executionId: ExecutionId = ExecutionId(UUID.randomUUID().toString())
    }

    class Instance(methodId: MethodId, val instance: Any, sourceDescription: String? = null) :
        ExecutionContext(methodId, sourceDescription) {

        override val executionId: ExecutionId = ExecutionId(UUID.randomUUID().toString());
    }
}