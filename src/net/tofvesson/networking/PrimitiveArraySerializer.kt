package net.tofvesson.networking

import java.lang.reflect.Field
import java.nio.ByteBuffer

class PrimitiveArraySerializer private constructor(): Serializer(arrayOf(
        BooleanArray::class.java,
        ByteArray::class.java,
        ShortArray::class.java,
        IntArray::class.java,
        LongArray::class.java,
        FloatArray::class.java,
        DoubleArray::class.java
)) {
    companion object {
        const val flagKnownSize = "knownSize"
        private val knownSize = SyncFlag.createFlag(flagKnownSize)
        val singleton = PrimitiveArraySerializer()
    }

    override fun computeSize(field: Field, flags: Array<out SyncFlag>, owner: Any?): Pair<Int, Int> {
        val arrayLength = java.lang.reflect.Array.getLength(field.get(owner))
        var byteSize = if(flags.contains(knownSize)) 0 else varIntSize(arrayLength.toLong())
        var bitSize = 0
        when (field.type) {
            BooleanArray::class.java -> bitSize = arrayLength
            ByteArray::class.java -> byteSize += arrayLength
            ShortArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) byteSize += arrayLength * 2
                else
                    for(value in field.get(owner) as ShortArray)
                        byteSize +=
                                varIntSize(
                                        if(flags.contains(SyncFlag.NonNegative)) value.toLong()
                                        else zigZagEncode(value.toLong())
                                )
            IntArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) byteSize += arrayLength * 4
                else
                    for(value in field.get(owner) as IntArray)
                        byteSize +=
                                varIntSize(
                                        if(flags.contains(SyncFlag.NonNegative)) value.toLong()
                                        else zigZagEncode(value.toLong())
                                )
            LongArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) byteSize += arrayLength * 8
                else
                    for(value in field.get(owner) as LongArray)
                        byteSize +=
                                varIntSize(
                                        if(flags.contains(SyncFlag.NonNegative)) value
                                        else zigZagEncode(value)
                                )
            FloatArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) byteSize += arrayLength * 4
                else
                    for (value in field.get(owner) as LongArray)
                        byteSize +=
                                varIntSize(
                                        if(flags.contains(SyncFlag.FloatEndianSwap)) bitConvert(swapEndian(floatToInt(field.getFloat(owner))))
                                        else bitConvert(floatToInt(field.getFloat(owner)))
                                )
            DoubleArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) byteSize += arrayLength * 8
                else
                    for (value in field.get(owner) as LongArray)
                        byteSize +=
                                varIntSize(
                                        if(flags.contains(SyncFlag.FloatEndianSwap)) swapEndian(doubleToLong(field.getDouble(owner)))
                                        else doubleToLong(field.getDouble(owner))
                                )
        }
        return Pair(byteSize, bitSize)
    }

    override fun serialize(field: Field, flags: Array<out SyncFlag>, owner: Any?, byteBuffer: ByteBuffer, offset: Int, bitFieldOffset: Int): Pair<Int, Int> {
        val arrayLength = java.lang.reflect.Array.getLength(field.get(owner))
        var localByteOffset = offset
        var localBitOffset = bitFieldOffset
        if(!flags.contains(knownSize)){
            writeVarInt(byteBuffer, offset, arrayLength.toLong())
            localByteOffset += varIntSize(arrayLength.toLong())
        }
        when (field.type) {
            BooleanArray::class.java ->
                for(value in field.get(owner) as BooleanArray)
                    writeBit(value, byteBuffer, localBitOffset++)
            ByteArray::class.java ->
                for(value in field.get(owner) as ByteArray)
                    byteBuffer.put(localByteOffset++, value)
            ShortArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as ShortArray){
                        byteBuffer.putShort(localByteOffset, value)
                        localByteOffset += 2
                    }
                else
                    for(value in field.get(owner) as ShortArray) {
                        val rawVal =
                                if(flags.contains(SyncFlag.NonNegative)) value.toLong()
                                else zigZagEncode(value.toLong())
                        writeVarInt(byteBuffer, localByteOffset, rawVal)
                        localByteOffset += varIntSize(rawVal)
                    }
            IntArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as IntArray){
                        byteBuffer.putInt(localByteOffset, value)
                        localByteOffset += 4
                    }
                else
                    for(value in field.get(owner) as IntArray) {
                        val rawVal =
                                if(flags.contains(SyncFlag.NonNegative)) value.toLong()
                                else zigZagEncode(value.toLong())
                        writeVarInt(byteBuffer, localByteOffset, rawVal)
                        localByteOffset += varIntSize(rawVal)
                    }
            LongArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as LongArray){
                        byteBuffer.putLong(localByteOffset, value)
                        localByteOffset += 8
                    }
                else
                    for(value in field.get(owner) as LongArray) {
                        val rawVal =
                                if(flags.contains(SyncFlag.NonNegative)) value
                                else zigZagEncode(value)
                        writeVarInt(
                                byteBuffer,
                                localByteOffset,
                                rawVal
                                )
                        localByteOffset += varIntSize(rawVal)
                    }
            FloatArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as FloatArray){
                        byteBuffer.putFloat(localByteOffset, value)
                        localByteOffset += 4
                    }
                else
                    for (value in field.get(owner) as FloatArray) {
                        val rawVal =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) bitConvert(swapEndian(floatToInt(field.getFloat(owner))))
                                else bitConvert(floatToInt(field.getFloat(owner)))
                        writeVarInt(byteBuffer, localByteOffset, rawVal)
                        localByteOffset += varIntSize(rawVal)
                    }
            DoubleArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as DoubleArray){
                        byteBuffer.putDouble(localByteOffset, value)
                        localByteOffset += 8
                    }
                else
                    for (value in field.get(owner) as DoubleArray){
                        val rawVal =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) swapEndian(doubleToLong(field.getDouble(owner)))
                                else doubleToLong(field.getDouble(owner))
                        writeVarInt(byteBuffer, localByteOffset, rawVal)
                        localByteOffset += varIntSize(rawVal)
                    }
        }
        return Pair(localByteOffset, localBitOffset)
    }

    override fun deserialize(field: Field, flags: Array<out SyncFlag>, owner: Any?, byteBuffer: ByteBuffer, offset: Int, bitFieldOffset: Int): Pair<Int, Int> {
        var localByteOffset = offset
        var localBitOffset = bitFieldOffset
        val localLength = java.lang.reflect.Array.getLength(field.get(owner))
        val arrayLength =
                if(flags.contains(knownSize)) localLength
                else{
                    val v = readVarInt(byteBuffer, offset)
                    localByteOffset += varIntSize(v)
                    v.toInt()
                }

        val target =
                if(arrayLength!=localLength) java.lang.reflect.Array.newInstance(field.type.componentType, arrayLength)
                else field.get(owner)

        when (field.type) {
            BooleanArray::class.java -> {
                val booleanTarget = target as BooleanArray
                for (index in 0 until arrayLength)
                    booleanTarget[index] = readBit(byteBuffer, localBitOffset++)
            }
            ByteArray::class.java -> {
                val byteTarget = target as ByteArray
                for (index in 0 until arrayLength)
                    byteTarget[index] = byteBuffer[localByteOffset++]
            }
            ShortArray::class.java -> {
                val shortTarget = target as ShortArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength) {
                        shortTarget[index] = byteBuffer.getShort(localByteOffset)
                        localByteOffset += 2
                    }
                else
                    for (index in 0 until arrayLength) {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) readVarInt(byteBuffer, offset)
                                else zigZagDecode(readVarInt(byteBuffer, offset))
                        shortTarget[index] = rawValue.toShort()
                        localByteOffset += varIntSize(rawValue)
                    }
            }
            IntArray::class.java -> {
                val intTarget = target as IntArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength) {
                        intTarget[index] = byteBuffer.getInt(localByteOffset)
                        localByteOffset += 4
                    }
                else
                    for (index in 0 until arrayLength) {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) readVarInt(byteBuffer, offset)
                                else zigZagDecode(readVarInt(byteBuffer, offset))
                        intTarget[index] = rawValue.toInt()
                        localByteOffset += varIntSize(rawValue)
                    }
            }
            LongArray::class.java -> {
                val longTarget = target as LongArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength) {
                        longTarget[index] = byteBuffer.getLong(localByteOffset)
                        localByteOffset += 8
                    }
                else
                    for (index in 0 until arrayLength) {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) readVarInt(byteBuffer, offset)
                                else zigZagDecode(readVarInt(byteBuffer, offset))
                        longTarget[index] = rawValue
                        localByteOffset += varIntSize(rawValue)
                    }
            }
            FloatArray::class.java -> {
                val floatTarget = target as FloatArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength) {
                        floatTarget[index] = byteBuffer.getFloat(localByteOffset)
                        localByteOffset += 4
                    }
                else
                    for (index in 0 until arrayLength) {
                        val readVal = readVarInt(byteBuffer, offset)
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) intToFloat(swapEndian(readVal.toInt()))
                                else intToFloat(readVal.toInt())
                        floatTarget[index] = rawValue
                        localBitOffset += varIntSize(readVal)
                    }
            }
            DoubleArray::class.java -> {
                val doubleTarget = target as DoubleArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength) {
                        doubleTarget[index] = byteBuffer.getDouble(localByteOffset)
                        localByteOffset += 8
                    }
                else
                    for (index in 0 until arrayLength) {
                        val readVal = readVarInt(byteBuffer, offset)
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) longToDouble(swapEndian(readVal))
                                else longToDouble(readVal)
                        doubleTarget[index] = rawValue
                        localBitOffset += varIntSize(readVal)
                    }
            }
        }
        if(arrayLength!=localLength) field.set(owner, target)
        return Pair(localByteOffset, localBitOffset)
    }
}