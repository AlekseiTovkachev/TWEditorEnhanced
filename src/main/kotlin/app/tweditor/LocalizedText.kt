package app.tweditor

/**
 * A user-facing message as data: a message key plus typed arguments. The Seam
 * stays GUI-free — only the shell renders, through the active EditorMessages
 * bundle; toString stays a stable diagnostic form.
 */
data class LocalizedText(val key: String, val args: List<Any> = emptyList()) {
    constructor(key: String, vararg args: Any) : this(key, args.toList())

    override fun toString(): String =
        if (args.isEmpty()) key else key + args.joinToString(prefix = "(", postfix = ")")
}
