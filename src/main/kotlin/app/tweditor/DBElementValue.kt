package app.tweditor

abstract class DBElementValue : Cloneable {
    public override fun clone(): DBElementValue {
        return try {
            super.clone() as DBElementValue
        } catch (exc: CloneNotSupportedException) {
            throw UnsupportedOperationException("Unable to clone database element value", exc)
        }
    }
}
