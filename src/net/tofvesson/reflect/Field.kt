package net.tofvesson.reflect

import java.lang.reflect.Field

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

fun Field.getBooleanAdaptive(obj: Any?) = if(this.type.isPrimitive) access().getBoolean(obj) else access().get(obj) as Boolean
fun Field.getByteAdaptive(obj: Any?) = if(this.type.isPrimitive) access().getByte(obj) else access().get(obj) as Byte
fun Field.getShortAdaptive(obj: Any?) = if(this.type.isPrimitive) access().getShort(obj) else access().get(obj) as Short
fun Field.getIntAdaptive(obj: Any?) = if(this.type.isPrimitive) access().getInt(obj) else access().get(obj) as Int
fun Field.getLongAdaptive(obj: Any?) = if(this.type.isPrimitive) access().getLong(obj) else access().get(obj) as Long
fun Field.getFloatAdaptive(obj: Any?) = if(this.type.isPrimitive) access().getFloat(obj) else access().get(obj) as Float
fun Field.getDoubleAdaptive(obj: Any?) = if(this.type.isPrimitive) access().getDouble(obj) else access().get(obj) as Double

fun Field.setBooleanAdaptive(obj: Any?, value: Boolean) = if(this.type.isPrimitive) access().setBoolean(obj, value) else access().set(obj, value)
fun Field.setByteAdaptive(obj: Any?, value: Byte) = if(this.type.isPrimitive) access().setByte(obj, value) else access().set(obj, value)
fun Field.setShortAdaptive(obj: Any?, value: Short) = if(this.type.isPrimitive) access().setShort(obj, value) else access().set(obj, value)
fun Field.setIntAdaptive(obj: Any?, value: Int) = if(this.type.isPrimitive) access().setInt(obj, value) else access().set(obj, value)
fun Field.setLongAdaptive(obj: Any?, value: Long) = if(this.type.isPrimitive) access().setLong(obj, value) else access().set(obj, value)
fun Field.setFloatAdaptive(obj: Any?, value: Float) = if(this.type.isPrimitive) access().setFloat(obj, value) else access().set(obj, value)
fun Field.setDoubleAdaptive(obj: Any?, value: Double) = if(this.type.isPrimitive) access().setDouble(obj, value) else access().set(obj, value)