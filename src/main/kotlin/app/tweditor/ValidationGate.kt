package app.tweditor

fun interface ValidationGate {
    fun validate(session: GameSession): List<String>
}
