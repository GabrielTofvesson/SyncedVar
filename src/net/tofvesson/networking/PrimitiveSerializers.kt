package net.tofvesson.networking

import net.tofvesson.reflect.*
import java.lang.reflect.Field

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
            state: WriteState,
            fieldType: Class<*>
    ) {
        when (fieldType) {
            java.lang.Boolean::class.java, Boolean::class.java -> state.registerBits(1)
            java.lang.Byte::class.java, Byte::class.java -> state.registerBytes(1)
            java.lang.Short::class.java, Short::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) state.registerBytes(2)
                else state.registerBytes(varIntSize(
                        if (flags.contains(SyncFlag.NonNegative)) field.getShortAdaptive(owner).toLong()
                        else zigZagEncode(field.getShortAdaptive(owner).toLong())
                ))
            java.lang.Integer::class.java, Int::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) state.registerBytes(4)
                else state.registerBytes(varIntSize(
                        if (flags.contains(SyncFlag.NonNegative)) field.getIntAdaptive(owner).toLong()
                        else zigZagEncode(field.getIntAdaptive(owner).toLong())
                ))
            java.lang.Long::class.java, Long::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) state.registerBytes(8)
                else state.registerBytes(varIntSize(
                        if (flags.contains(SyncFlag.NonNegative)) field.getLongAdaptive(owner)
                        else zigZagEncode(field.getLongAdaptive(owner))
                ))
            java.lang.Float::class.java, Float::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) state.registerBytes(4)
                else state.registerBytes(varIntSize(
                        if (flags.contains(SyncFlag.FloatEndianSwap)) bitConvert(swapEndian(floatToInt(field.getFloatAdaptive(owner))))
                        else bitConvert(floatToInt(field.getFloatAdaptive(owner)))
                ))
            java.lang.Double::class.java, Double::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) state.registerBytes(8)
                else state.registerBytes(varIntSize(
                        if (flags.contains(SyncFlag.FloatEndianSwap)) swapEndian(doubleToLong(field.getDoubleAdaptive(owner)))
                        else doubleToLong(field.getDoubleAdaptive(owner))
                ))
            else -> throwInvalidType(fieldType)
        }
    }

    override fun serializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            writeBuffer: WriteBuffer,
            fieldType: Class<*>
    ){
        when (fieldType) {
            java.lang.Boolean::class.java, Boolean::class.java -> writeBuffer.writeBit(field.getBooleanAdaptive(owner))
            java.lang.Byte::class.java, Byte::class.java -> writeBuffer.writeByte(field.getByteAdaptive(owner))
            java.lang.Short::class.java, Short::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) writeBuffer.writeShort(field.getShortAdaptive(owner))
                else writeBuffer.writePackedShort(field.getShortAdaptive(owner), flags.contains(SyncFlag.NonNegative))
            java.lang.Integer::class.java, Int::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) writeBuffer.writeInt(field.getIntAdaptive(owner))
                else writeBuffer.writePackedInt(field.getIntAdaptive(owner), flags.contains(SyncFlag.NonNegative))
            java.lang.Long::class.java, Long::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) writeBuffer.writeLong(field.getLongAdaptive(owner))
                else writeBuffer.writePackedLong(field.getLongAdaptive(owner), flags.contains(SyncFlag.NonNegative))
            java.lang.Float::class.java, Float::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) writeBuffer.writeFloat(field.getFloatAdaptive(owner))
                else writeBuffer.writePackedFloat(field.getFloatAdaptive(owner), flags.contains(SyncFlag.FloatEndianSwap))
            java.lang.Double::class.java, Double::class.java ->
                if (flags.contains(SyncFlag.NoCompress)) writeBuffer.writeDouble(field.getDoubleAdaptive(owner))
                else writeBuffer.writePackedDouble(field.getDoubleAdaptive(owner), flags.contains(SyncFlag.FloatEndianSwap))
            else -> throwInvalidType(fieldType)
        }
    }

    override fun deserializeExplicit(
            field: Field,
            flags: Array<out SyncFlag>,
            owner: Any?,
            readBuffer: ReadBuffer,
            fieldType: Class<*>
    ) =
            when(fieldType){
                java.lang.Boolean::class.java, Boolean::class.java -> field.setBooleanAdaptive(owner, readBuffer.readBit())
                java.lang.Byte::class.java, Byte::class.java -> field.setByteAdaptive(owner, readBuffer.readByte())
                java.lang.Short::class.java, Short::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) field.setShortAdaptive(owner, readBuffer.readShort())
                    else field.setShortAdaptive(owner, readBuffer.readPackedShort(flags.contains(SyncFlag.NonNegative)))
                java.lang.Integer::class.java, Int::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) field.setIntAdaptive(owner, readBuffer.readInt())
                    else field.setIntAdaptive(owner, readBuffer.readPackedInt(flags.contains(SyncFlag.NonNegative)))
                java.lang.Long::class.java, Long::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) field.setLongAdaptive(owner, readBuffer.readLong())
                    else field.setLongAdaptive(owner, readBuffer.readPackedLong(flags.contains(SyncFlag.NonNegative)))
                java.lang.Float::class.java, Float::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) field.setFloatAdaptive(owner, readBuffer.readFloat())
                    else field.setFloatAdaptive(owner, readBuffer.readPackedFloat(flags.contains(SyncFlag.FloatEndianSwap)))
                java.lang.Double::class.java, Double::class.java ->
                    if(flags.contains(SyncFlag.NoCompress)) field.setDoubleAdaptive(owner, readBuffer.readDouble())
                    else field.setDoubleAdaptive(owner, readBuffer.readPackedDouble(flags.contains(SyncFlag.FloatEndianSwap)))
                else -> throwInvalidType(fieldType)
            }
}