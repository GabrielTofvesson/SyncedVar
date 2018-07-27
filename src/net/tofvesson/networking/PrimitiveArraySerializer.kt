package net.tofvesson.networking

import java.lang.reflect.Field

@Suppress("MemberVisibilityCanBePrivate")
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

    override fun computeSizeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, state: WriteState, fieldType: Class<*>) {
        val arrayLength = java.lang.reflect.Array.getLength(field.get(owner))
        val holder = Holder(null)
        when (fieldType) {
            BooleanArray::class.java -> state.registerBits(arrayLength)
            ByteArray::class.java -> state.registerBytes(arrayLength)
            ShortArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) state.registerBytes(arrayLength * 2)
                else {
                    val shortSerializer = SyncHandler.getCompatibleSerializer(Short::class.java)
                    for (value in field.get(owner) as ShortArray) {
                        holder.value = value
                        shortSerializer.computeSizeExplicit(Holder.valueField, flags, holder, state, Short::class.java)
                    }
                }
            IntArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) state.registerBytes(arrayLength * 4)
                else {
                    val intSerializer = SyncHandler.getCompatibleSerializer(Int::class.java)
                    for (value in field.get(owner) as IntArray) {
                        holder.value = value
                        intSerializer.computeSizeExplicit(Holder.valueField, flags, holder, state, Int::class.java)
                    }
                }
            LongArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) state.registerBytes(arrayLength * 8)
                else {
                    val longSerializer = SyncHandler.getCompatibleSerializer(Long::class.java)
                    for (value in field.get(owner) as LongArray) {
                        holder.value = value
                        longSerializer.computeSizeExplicit(Holder.valueField, flags, holder, state, Long::class.java)
                    }
                }
            FloatArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) state.registerBytes(arrayLength * 4)
                else {
                    val floatSerializer = SyncHandler.getCompatibleSerializer(Float::class.java)
                    for (value in field.get(owner) as FloatArray) {
                        holder.value = value
                        floatSerializer.computeSizeExplicit(Holder.valueField, flags, holder, state, Float::class.java)
                    }
                }
            DoubleArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress)) state.registerBytes(arrayLength * 8)
                else {
                    val doubleSerializer = SyncHandler.getCompatibleSerializer(Double::class.java)
                    for (value in field.get(owner) as DoubleArray) {
                        holder.value = value
                        doubleSerializer.computeSizeExplicit(Holder.valueField, flags, holder, state, Double::class.java)
                    }
                }
            else -> throwInvalidType(fieldType)
        }
    }

    override fun serializeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, writeBuffer: WriteBuffer, fieldType: Class<*>) {
        val arrayLength = java.lang.reflect.Array.getLength(field.get(owner))
        val holder = Holder(null)
        if(!flags.contains(knownSize)) writeBuffer.writePackedInt(arrayLength, true)
        when (fieldType) {
            BooleanArray::class.java ->
                for(value in field.get(owner) as BooleanArray)
                    writeBuffer.writeBit(value)
            ByteArray::class.java ->
                for(value in field.get(owner) as ByteArray)
                    writeBuffer.writeByte(value)
            ShortArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as ShortArray)
                        writeBuffer.writeShort(value)
                else {
                    val shortSerializer = SyncHandler.getCompatibleSerializer(Short::class.java)
                    for (value in field.get(owner) as ShortArray) {
                        holder.value = value
                        shortSerializer.serializeExplicit(Holder.valueField, flags, holder, writeBuffer, fieldType)
                    }
                }
            IntArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as IntArray)
                        writeBuffer.writeInt(value)
                else {
                    val intSerializer = SyncHandler.getCompatibleSerializer(Int::class.java)
                    for (value in field.get(owner) as IntArray) {
                        holder.value = value
                        intSerializer.serializeExplicit(Holder.valueField, flags, holder, writeBuffer, fieldType)
                    }
                }
            LongArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as LongArray)
                        writeBuffer.writeLong(value)
                else {
                    val longSerializer = SyncHandler.getCompatibleSerializer(Long::class.java)
                    for (value in field.get(owner) as LongArray) {
                        holder.value = value
                        longSerializer.serializeExplicit(Holder.valueField, flags, holder, writeBuffer, fieldType)
                    }
                }
            FloatArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as FloatArray)
                        writeBuffer.writeFloat(value)
                else {
                    val floatSerializer = SyncHandler.getCompatibleSerializer(Float::class.java)
                    for (value in field.get(owner) as FloatArray) {
                        holder.value = value
                        floatSerializer.serializeExplicit(Holder.valueField, flags, holder, writeBuffer, fieldType)
                    }
                }
            DoubleArray::class.java ->
                if(flags.contains(SyncFlag.NoCompress))
                    for(value in field.get(owner) as DoubleArray)
                        writeBuffer.writeDouble(value)
                else {
                    val doubleSerializer = SyncHandler.getCompatibleSerializer(Double::class.java)
                    for (value in field.get(owner) as DoubleArray) {
                        holder.value = value
                        doubleSerializer.serializeExplicit(Holder.valueField, flags, holder, writeBuffer, fieldType)
                    }
                }
            else -> throwInvalidType(fieldType)
        }
    }

    override fun deserializeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, readBuffer: ReadBuffer, fieldType: Class<*>) {
        val localLength = java.lang.reflect.Array.getLength(field.get(owner))
        val arrayLength =
                if(flags.contains(knownSize)) localLength
                else readBuffer.readPackedInt(true)

        val target =
                if(arrayLength!=localLength) java.lang.reflect.Array.newInstance(field.type.componentType, arrayLength)
                else field.get(owner)

        when (fieldType) {
            BooleanArray::class.java -> {
                val booleanTarget = target as BooleanArray
                for (index in 0 until arrayLength)
                    booleanTarget[index] = readBuffer.readBit()
            }
            ByteArray::class.java -> {
                val byteTarget = target as ByteArray
                for (index in 0 until arrayLength)
                    byteTarget[index] = readBuffer.readByte()
            }
            ShortArray::class.java -> {
                val shortTarget = target as ShortArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength)
                        shortTarget[index] = readBuffer.readShort()
                else
                    for (index in 0 until arrayLength)
                        shortTarget[index] = readBuffer.readPackedShort(flags.contains(SyncFlag.NonNegative))
            }
            IntArray::class.java -> {
                val intTarget = target as IntArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength)
                        intTarget[index] = readBuffer.readInt()
                else
                    for (index in 0 until arrayLength)
                        intTarget[index] = readBuffer.readPackedInt(flags.contains(SyncFlag.NonNegative))
            }
            LongArray::class.java -> {
                val longTarget = target as LongArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength)
                        longTarget[index] = readBuffer.readLong()
                else
                    for (index in 0 until arrayLength)
                        longTarget[index] = readBuffer.readPackedLong(flags.contains(SyncFlag.NonNegative))
            }
            FloatArray::class.java -> {
                val floatTarget = target as FloatArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength)
                        floatTarget[index] = readBuffer.readFloat()
                else
                    for (index in 0 until arrayLength)
                        floatTarget[index] = readBuffer.readPackedFloat(flags.contains(SyncFlag.NonNegative))
            }
            DoubleArray::class.java -> {
                val doubleTarget = target as DoubleArray
                if (flags.contains(SyncFlag.NoCompress))
                    for (index in 0 until arrayLength)
                        doubleTarget[index] = readBuffer.readDouble()
                else
                    for (index in 0 until arrayLength)
                        doubleTarget[index] = readBuffer.readPackedDouble(flags.contains(SyncFlag.NonNegative))
            }
            else -> throwInvalidType(fieldType)
        }
        if(arrayLength!=localLength) field.set(owner, target)
    }
}