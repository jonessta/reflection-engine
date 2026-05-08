package au.clef.engine

fun interface TerminalTypeDecider {
    fun isTerminal(type: Class<*>): Boolean
}