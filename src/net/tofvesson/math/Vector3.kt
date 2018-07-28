package net.tofvesson.math

import net.tofvesson.data.DiffTracked

class Vector3(xPos: Float, yPos: Float, zPos: Float) {

    private val _x = DiffTracked(xPos, Float::class.java)
    private val _y = DiffTracked(yPos, Float::class.java)
    private val _z = DiffTracked(zPos, Float::class.java)


    var x: Float
        get() = _x.value
        set(value) {
            _x.value = value
        }
    var y: Float
        get() = _y.value
        set(value) {
            _y.value = value
        }
    var z: Float
        get() = _z.value
        set(value) {
            _z.value = value
        }

    override fun toString() = "($x; $y; $z)"
}