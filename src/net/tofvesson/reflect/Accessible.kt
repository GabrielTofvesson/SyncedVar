package net.tofvesson.reflect

import sun.misc.Unsafe
import java.lang.reflect.AccessibleObject

var AccessibleObject.forceAccessible: Boolean
    get() = this.isAccessible
    set(value) {
        val fieldOverride = AccessibleObject::class.java.getDeclaredField("override")
        val unsafe = getUnsafe()
        val overrideOffset = unsafe.objectFieldOffset(fieldOverride)
        unsafe.getAndSetObject(this, overrideOffset, value)
    }

fun <T> T.access(): T where T: AccessibleObject {
    this.forceAccessible = true
    return this
}

fun getUnsafe(): Unsafe{
    val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    theUnsafe.isAccessible = true
    return theUnsafe.get(null) as Unsafe
}