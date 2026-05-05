package au.clef.engine.model

sealed class InheritanceLevel {
    /**
     * only methods in the class itself
     */
    data object DeclaredOnly : InheritanceLevel()

    /**
     * full chain up to Any
     */
    data object All : InheritanceLevel()

    /**
     * Depth(1): class + direct parent
     * Depth(2): class + parent + grandparent
     */
    data class Depth(val depth: Int) : InheritanceLevel() {

        init {
            require(depth >= 0) { "Depth must be >= 0" }
        }
    }
}