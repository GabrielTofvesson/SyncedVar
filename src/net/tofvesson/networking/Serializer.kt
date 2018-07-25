package net.tofvesson.networking

import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.util.*

abstract class Serializer(registeredTypes: Array<Class<*>>) {
    private val registeredTypes: Array<Class<*>> = Arrays.copyOf(registeredTypes, registeredTypes.size)

    /**
     * Compute the size in bits that a field will occupy
     * @return Size in the byteField (first) and bitField (second) that need to be allocated
     */
    fun computeSize(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?
    ): Pair<Int, Int> = computeSizeExplicit(field, flags, owner, field.type)

    abstract fun computeSizeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            fieldType: Class<*>
    ): Pair<Int, Int>

    /**
     * Serialize a field to the buffer
     * @return The new offset (first) and bitFieldOffset (second)
     */
    fun serialize(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            byteBuffer: ByteBuffer,
            offset: Int,
            bitFieldOffset: Int
    ): Pair<Int, Int> = serializeExplicit(field, flags, owner, byteBuffer, offset, bitFieldOffset, field.type)

    abstract fun serializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            byteBuffer: ByteBuffer,
            offset: Int,
            bitFieldOffset: Int,
            fieldType: Class<*>
    ): Pair<Int, Int>

    /**
     * Deserialize a field from the buffer
     * @return The new offset (first) and bitFieldOffset (second)
     */
    fun deserialize(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            byteBuffer: ByteBuffer,
            offset: Int,
            bitFieldOffset: Int
    ): Pair<Int, Int> = deserializeExplicit(field, flags, owner, byteBuffer, offset, bitFieldOffset, field.type)

    abstract fun deserializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            byteBuffer: ByteBuffer,
            offset: Int,
            bitFieldOffset: Int,
            fieldType: Class<*>
    ): Pair<Int, Int>

    fun getRegisteredTypes(): Array<Class<*>> = Arrays.copyOf(registeredTypes, registeredTypes.size)
    fun canSerialize(field: Field): Boolean = registeredTypes.contains(field.type)
    fun canSerialize(type: Class<*>): Boolean = registeredTypes.contains(type)
}