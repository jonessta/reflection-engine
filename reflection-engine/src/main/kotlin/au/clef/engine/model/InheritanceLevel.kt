package au.clef.engine.model

sealed class InheritanceLevel {

    abstract val depth: Int

    /**
     * only methods in the class itself
     */
    data object DeclaredOnly : InheritanceLevel() {
        override val depth: Int = 0
    }

    /**
     * full chain up to Any
     */
    data object All : InheritanceLevel() {
        override val depth: Int = Int.MAX_VALUE
    }

    /**
     * Depth(1): class + direct parent
     * Depth(2): class + parent + grandparent
     */
    data class Depth(override val depth: Int) : InheritanceLevel() {
        init {
            require(depth >= 0) { "Depth must be >= 0" }
        }
    }
}