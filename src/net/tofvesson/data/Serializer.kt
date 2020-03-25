package net.tofvesson.data

import net.tofvesson.annotation.SyncFlag
import net.tofvesson.exception.UnsupportedTypeException
import net.tofvesson.reflect.access
import java.lang.reflect.Field
import java.util.*

abstract class Serializer(registeredTypes: Array<Class<*>>) {
    private val registeredTypes: Array<Class<*>> = Arrays.copyOf(registeredTypes, registeredTypes.size)

    /**
     * Compute the size in bits that a field will occupy
     */
    fun computeSize(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            state: WriteState
    ) = if (canSerialize(field.get(owner), flags, field.type))
            computeSizeExplicit(field, flags, owner, state, field.type)
        else Unit

    abstract fun computeSizeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            state: WriteState,
            fieldType: Class<*>
    )

    /**
     * Serialize a field to the buffer
     * @return The new offset (first) and bitFieldOffset (second)
     */
    fun serialize(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            writeBuffer: WBuffer
    ) = if (canSerialize(field.get(owner), flags, field.type))
            serializeExplicit(field.access(), flags, owner, writeBuffer, field.type)
        else Unit

    abstract fun serializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            writeBuffer: WBuffer,
            fieldType: Class<*>
    )

    /**
     * Deserialize a field from the buffer
     * @return The new offset (first) and bitFieldOffset (second)
     */
    fun deserialize(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            readBuffer: RBuffer
    ) = if (canDeserialize(field.get(owner), flags, field.type))
            deserializeExplicit(field.access(), flags, owner, readBuffer, field.type)
        else Unit

    abstract fun deserializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            readBuffer: RBuffer,
            fieldType: Class<*>
    )

    abstract fun canSerialize(obj: Any?, flags: Array<out SyncFlag>, type: Class<*>): Boolean
    open fun canDeserialize(obj: Any?, flags: Array<out SyncFlag>, type: Class<*>) = canSerialize(obj, flags, type)

    fun getRegisteredTypes(): Array<Class<*>> = Arrays.copyOf(registeredTypes, registeredTypes.size)
    fun canSerialize(field: Field) = canSerialize(field.type)
    open fun canSerialize(type: Class<*>): Boolean = registeredTypes.contains(type)

    protected fun throwInvalidType(type: Class<*>): Nothing = throw UnsupportedTypeException("Type ${this.javaClass} cannot serialize $type")
}