package net.tofvesson.data

import java.util.*

class DiffTracked<T>(initial: T, val valueType: Class<T>) {
    private var changed = false
    private var _value = initial
    var value: T
        get() = _value
        set(value) {
            changed = changed or (value != this._value)
            _value = value
        }
    fun hasChanged() = changed
    fun clearChangeState() { changed = false }
    override fun toString() = Objects.toString(_value)!!
}