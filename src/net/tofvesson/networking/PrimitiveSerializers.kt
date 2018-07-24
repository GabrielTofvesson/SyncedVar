package net.tofvesson.networking

import java.lang.reflect.Field
import java.nio.ByteBuffer

class PrimitiveSerializer private constructor() : Serializer(arrayOf(
        Boolean::class.java,
        Byte::class.java,
        Short::class.java,
        Int::class.java,
        Long::class.java,
        Float::class.java,
        Double::class.java
)) {
    companion object { val singleton = PrimitiveSerializer() }

    override fun computeSize(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?
    ): Pair<Int, Int> =
            when(field.type){
                Boolean::class.java -> Pair(0, 1)
                Byte::class.java -> Pair(1, 0)
                Short::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(2, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.NonNegative)) field.getShort(owner).toLong()
                            else zigZagEncode(field.getShort(owner).toLong())
                    ), 0)
                Int::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(4, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.NonNegative)) field.getInt(owner).toLong()
                            else zigZagEncode(field.getInt(owner).toLong())
                    ), 0)
                Long::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(8, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.NonNegative)) field.getLong(owner)
                            else zigZagEncode(field.getLong(owner))
                    ), 0)
                Float::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(4, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.FloatEndianSwap)) bitConvert(swapEndian(floatToInt(field.getFloat(owner))))
                            else bitConvert(floatToInt(field.getFloat(owner)))
                    ), 0)
                Double::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(8, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.FloatEndianSwap)) swapEndian(doubleToLong(field.getDouble(owner)))
                            else doubleToLong(field.getDouble(owner))
                    ), 0)
                else -> Pair(0, 0)
            }

    override fun serialize(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            byteBuffer: ByteBuffer,
            offset: Int,
            bitFieldOffset: Int
    ): Pair<Int, Int> =
            when(field.type){
                Boolean::class.java -> {
                    writeBit(field.getBoolean(owner), byteBuffer, bitFieldOffset)
                    Pair(offset, bitFieldOffset+1)
                }
                Byte::class.java -> {
                    byteBuffer.put(offset, field.getByte(owner))
                    Pair(offset+1, bitFieldOffset)
                }
                Short::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putShort(offset, field.getShort(owner))
                        Pair(offset+2, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) field.getShort(owner).toLong()
                                else zigZagEncode(field.getShort(owner).toLong())
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                Int::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putInt(offset, field.getInt(owner))
                        Pair(offset+4, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) field.getInt(owner).toLong()
                                else zigZagEncode(field.getInt(owner).toLong())
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                Long::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putLong(offset, field.getLong(owner))
                        Pair(offset+8, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) field.getLong(owner)
                                else zigZagEncode(field.getLong(owner))
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                Float::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putFloat(offset, field.getFloat(owner))
                        Pair(offset+4, bitFieldOffset)
                    }
                    else{
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) bitConvert(swapEndian(floatToInt(field.getFloat(owner))))
                                else bitConvert(floatToInt(field.getFloat(owner)))
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                Double::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putDouble(offset, field.getDouble(owner))
                        Pair(offset+8, bitFieldOffset)
                    }
                    else{
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) swapEndian(doubleToLong(field.getDouble(owner)))
                                else doubleToLong(field.getDouble(owner))
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                else -> Pair(offset, bitFieldOffset)
            }

    override fun deserialize(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            byteBuffer: ByteBuffer,
            offset: Int,
            bitFieldOffset: Int
    ): Pair<Int, Int> =
            when(field.type){
                Boolean::class.java -> {
                    field.setBoolean(owner, readBit(byteBuffer, bitFieldOffset))
                    Pair(offset, bitFieldOffset+1)
                }
                Byte::class.java -> {
                    field.setByte(owner, byteBuffer.get(offset))
                    Pair(offset+1, bitFieldOffset)
                }
                Short::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setShort(owner, byteBuffer.getShort(offset))
                        Pair(offset+2, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) readVarInt(byteBuffer, offset)
                                else zigZagDecode(readVarInt(byteBuffer, offset))
                        field.setShort(owner, rawValue.toShort())
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                Int::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setInt(owner, byteBuffer.getInt(offset))
                        Pair(offset+4, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) readVarInt(byteBuffer, offset)
                                else zigZagDecode(readVarInt(byteBuffer, offset))
                        field.setInt(owner, rawValue.toInt())
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                Long::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setLong(owner, byteBuffer.getLong(offset))
                        Pair(offset+8, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) readVarInt(byteBuffer, offset)
                                else zigZagDecode(readVarInt(byteBuffer, offset))
                        field.setLong(owner, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                Float::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setFloat(owner, byteBuffer.getFloat(offset))
                        Pair(offset+4, bitFieldOffset)
                    }
                    else{
                        val readVal = readVarInt(byteBuffer, offset)
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) intToFloat(swapEndian(readVal.toInt()))
                                else intToFloat(readVal.toInt())
                        field.setFloat(owner, rawValue)
                        Pair(offset+varIntSize(readVal), bitFieldOffset)
                    }
                Double::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setDouble(owner, byteBuffer.getDouble(offset))
                        Pair(offset+8, bitFieldOffset)
                    }
                    else{
                        val readVal = readVarInt(byteBuffer, offset)
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) longToDouble(swapEndian(readVal))
                                else longToDouble(readVal)
                        field.setDouble(owner, rawValue)
                        Pair(offset+varIntSize(readVal), bitFieldOffset)
                    }
                else -> Pair(offset, bitFieldOffset)
            }
}