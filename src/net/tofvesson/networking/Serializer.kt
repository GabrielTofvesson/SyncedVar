package net.tofvesson.networking

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
    ) = computeSizeExplicit(field, flags, owner, state, field.type)

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
            writeBuffer: WriteBuffer
    ) = serializeExplicit(field, flags, owner, writeBuffer, field.type)

    abstract fun serializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            writeBuffer: WriteBuffer,
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
            readBuffer: ReadBuffer
    ) = deserializeExplicit(field, flags, owner, readBuffer, field.type)

    abstract fun deserializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            readBuffer: ReadBuffer,
            fieldType: Class<*>
    )

    fun getRegisteredTypes(): Array<Class<*>> = Arrays.copyOf(registeredTypes, registeredTypes.size)
    fun canSerialize(field: Field): Boolean = registeredTypes.contains(field.type)
    fun canSerialize(type: Class<*>): Boolean = registeredTypes.contains(type)

    protected fun throwInvalidType(type: Class<*>): Nothing = throw UnsupportedTypeException("Type ${this.javaClass} cannot serialize $type")
}