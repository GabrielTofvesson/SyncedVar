package net.tofvesson.networking

import net.tofvesson.reflect.*
import java.lang.reflect.Field
import java.nio.ByteBuffer

class PrimitiveSerializer private constructor() : Serializer(arrayOf(
        Boolean::class.java,
        java.lang.Boolean::class.java,
        Byte::class.java,
        java.lang.Byte::class.java,
        Short::class.java,
        java.lang.Short::class.java,
        Int::class.java,
        java.lang.Integer::class.java,
        Long::class.java,
        java.lang.Long::class.java,
        Float::class.java,
        java.lang.Float::class.java,
        Double::class.java,
        java.lang.Double::class.java
)) {
    companion object { val singleton = PrimitiveSerializer() }

    override fun computeSizeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            fieldType: Class<*>
    ): Pair<Int, Int> =
            when(fieldType){
                java.lang.Boolean::class.java, Boolean::class.java -> Pair(0, 1)
                java.lang.Byte::class.java, Byte::class.java -> Pair(1, 0)
                java.lang.Short::class.java, Short::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(2, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.NonNegative)) field.getShortAdaptive(owner).toLong()
                            else zigZagEncode(field.getShortAdaptive(owner).toLong())
                    ), 0)
                java.lang.Integer::class.java, Int::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(4, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.NonNegative)) field.getIntAdaptive(owner).toLong()
                            else zigZagEncode(field.getIntAdaptive(owner).toLong())
                    ), 0)
                java.lang.Long::class.java, Long::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(8, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.NonNegative)) field.getLongAdaptive(owner)
                            else zigZagEncode(field.getLongAdaptive(owner))
                    ), 0)
                java.lang.Float::class.java, Float::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(4, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.FloatEndianSwap)) bitConvert(swapEndian(floatToInt(field.getFloatAdaptive(owner))))
                            else bitConvert(floatToInt(field.getFloatAdaptive(owner)))
                    ), 0)
                java.lang.Double::class.java, Double::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) Pair(8, 0)
                    else Pair(varIntSize(
                            if(flags.contains(SyncFlag.FloatEndianSwap)) swapEndian(doubleToLong(field.getDoubleAdaptive(owner)))
                            else doubleToLong(field.getDoubleAdaptive(owner))
                    ), 0)
                else -> Pair(0, 0)
            }

    override fun serializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            byteBuffer: ByteBuffer,
            offset: Int,
            bitFieldOffset: Int,
            fieldType: Class<*>
    ): Pair<Int, Int> =
            when(fieldType){
                java.lang.Boolean::class.java, Boolean::class.java -> {
                    writeBit(field.getBooleanAdaptive(owner), byteBuffer, bitFieldOffset)
                    Pair(offset, bitFieldOffset+1)
                }
                java.lang.Byte::class.java, Byte::class.java -> {
                    byteBuffer.put(offset, field.getByteAdaptive(owner))
                    Pair(offset+1, bitFieldOffset)
                }
                java.lang.Short::class.java, Short::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putShort(offset, field.getShortAdaptive(owner))
                        Pair(offset+2, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) field.getShortAdaptive(owner).toLong()
                                else zigZagEncode(field.getShortAdaptive(owner).toLong())
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                java.lang.Integer::class.java, Int::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putInt(offset, field.getIntAdaptive(owner))
                        Pair(offset+4, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) field.getIntAdaptive(owner).toLong()
                                else zigZagEncode(field.getIntAdaptive(owner).toLong())
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                java.lang.Long::class.java, Long::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putLong(offset, field.getLongAdaptive(owner))
                        Pair(offset+8, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) field.getLongAdaptive(owner)
                                else zigZagEncode(field.getLongAdaptive(owner))
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                java.lang.Float::class.java, Float::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putFloat(offset, field.getFloatAdaptive(owner))
                        Pair(offset+4, bitFieldOffset)
                    }
                    else{
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) bitConvert(swapEndian(floatToInt(field.getFloatAdaptive(owner))))
                                else bitConvert(floatToInt(field.getFloatAdaptive(owner)))
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                java.lang.Double::class.java, Double::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        byteBuffer.putDouble(offset, field.getDoubleAdaptive(owner))
                        Pair(offset+8, bitFieldOffset)
                    }
                    else{
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) swapEndian(doubleToLong(field.getDoubleAdaptive(owner)))
                                else doubleToLong(field.getDoubleAdaptive(owner))
                        writeVarInt(byteBuffer, offset, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                else -> Pair(offset, bitFieldOffset)
            }

    override fun deserializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            byteBuffer: ByteBuffer,
            offset: Int,
            bitFieldOffset: Int,
            fieldType: Class<*>
    ): Pair<Int, Int> =
            when(fieldType){
                java.lang.Boolean::class.java, Boolean::class.java -> {
                    field.setBooleanAdaptive(owner, readBit(byteBuffer, bitFieldOffset))
                    Pair(offset, bitFieldOffset+1)
                }
                java.lang.Byte::class.java, Byte::class.java -> {
                    field.setByteAdaptive(owner, byteBuffer.get(offset))
                    Pair(offset+1, bitFieldOffset)
                }
                java.lang.Short::class.java, Short::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setShortAdaptive(owner, byteBuffer.getShort(offset))
                        Pair(offset+2, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) readVarInt(byteBuffer, offset)
                                else zigZagDecode(readVarInt(byteBuffer, offset))
                        field.setShortAdaptive(owner, rawValue.toShort())
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                java.lang.Integer::class.java, Int::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setIntAdaptive(owner, byteBuffer.getInt(offset))
                        Pair(offset+4, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) readVarInt(byteBuffer, offset)
                                else zigZagDecode(readVarInt(byteBuffer, offset))
                        field.setIntAdaptive(owner, rawValue.toInt())
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                java.lang.Long::class.java, Long::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setLongAdaptive(owner, byteBuffer.getLong(offset))
                        Pair(offset+8, bitFieldOffset)
                    }
                    else {
                        val rawValue =
                                if(flags.contains(SyncFlag.NonNegative)) readVarInt(byteBuffer, offset)
                                else zigZagDecode(readVarInt(byteBuffer, offset))
                        field.setLongAdaptive(owner, rawValue)
                        Pair(offset+varIntSize(rawValue), bitFieldOffset)
                    }
                java.lang.Float::class.java, Float::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setFloatAdaptive(owner, byteBuffer.getFloat(offset))
                        Pair(offset+4, bitFieldOffset)
                    }
                    else{
                        val readVal = readVarInt(byteBuffer, offset)
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) intToFloat(swapEndian(readVal.toInt()))
                                else intToFloat(readVal.toInt())
                        field.setFloatAdaptive(owner, rawValue)
                        Pair(offset+varIntSize(readVal), bitFieldOffset)
                    }
                java.lang.Double::class.java, Double::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)){
                        field.setDoubleAdaptive(owner, byteBuffer.getDouble(offset))
                        Pair(offset+8, bitFieldOffset)
                    }
                    else{
                        val readVal = readVarInt(byteBuffer, offset)
                        val rawValue =
                                if(flags.contains(SyncFlag.FloatEndianSwap)) longToDouble(swapEndian(readVal))
                                else longToDouble(readVal)
                        field.setDoubleAdaptive(owner, rawValue)
                        Pair(offset+varIntSize(readVal), bitFieldOffset)
                    }
                else -> Pair(offset, bitFieldOffset)
            }
}