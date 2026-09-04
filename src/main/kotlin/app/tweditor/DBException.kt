package app.tweditor

class DBException : Exception {
    constructor()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
