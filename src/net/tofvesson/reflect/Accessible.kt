package net.tofvesson.reflect

import sun.misc.Unsafe
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field

var AccessibleObject.forceAccessible: Boolean
    get() = this.isAccessible
    set(value) {
        val fieldOverride = AccessibleObject::class.java.getDeclaredField("override")
        val unsafe = getUnsafe()
        val overrideOffset = unsafe.objectFieldOffset(fieldOverride)
        unsafe.getAndSetObject(this, overrideOffset, value)
    }

fun getUnsafe(): Unsafe{
    val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    theUnsafe.trySetAccessible()
    return theUnsafe.get(null) as Unsafe
}

fun Field.setStaticFinalValue(value: Any?){
    val factory = Class.forName("jdk.internal.reflect.UnsafeFieldAccessorFactory").getDeclaredMethod("newFieldAccessor", Field::class.java, Boolean::class.java)
    val isReadonly = Class.forName("jdk.internal.reflect.UnsafeQualifiedStaticFieldAccessorImpl").getDeclaredField("isReadOnly")
    isReadonly.forceAccessible = true
    factory.forceAccessible = true
    val overrideAccessor = Field::class.java.getDeclaredField("overrideFieldAccessor")
    overrideAccessor.forceAccessible = true
    val accessor = factory.invoke(null, this, true)
    isReadonly.setBoolean(accessor, false)
    overrideAccessor.set(this, accessor)
    this.forceAccessible = true
    this.set(null, value)
}