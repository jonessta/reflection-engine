package au.clef.engine

import au.clef.engine.model.ExecutionId
import au.clef.engine.model.MethodId
import java.util.*

sealed class ExecutionContext(val methodId: MethodId, val sourceDescription: String? = null) {

    abstract val executionId: ExecutionId

    class Static(methodId: MethodId, sourceDescription: String? = null) :
        ExecutionContext(methodId, sourceDescription) {

        override val executionId: ExecutionId = ExecutionId("static:${methodId}")
    }

    class Instance(val instance: Any, sourceDescription: String? = null, methodId: MethodId) :
        ExecutionContext(methodId, sourceDescription) {

        override val executionId: ExecutionId =
            ExecutionId("instance:${UUID.randomUUID()}:${methodId}")
    }
}