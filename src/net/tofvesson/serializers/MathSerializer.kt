package net.tofvesson.serializers

import net.tofvesson.annotation.SyncFlag
import net.tofvesson.data.*
import net.tofvesson.exception.MismatchedFlagException
import net.tofvesson.math.*
import net.tofvesson.reflect.access
import java.lang.reflect.Field

@Suppress("UNCHECKED_CAST", "MemberVisibilityCanBePrivate")
class MathSerializer: Serializer(arrayOf(
        Vector3::class.java
)){
    companion object {
        val singleton = MathSerializer()
        const val flagHighCompressionRotation = "rotCompress1"
        const val flagMidCompressionRotation = "rotCompress2"
        const val flagLowCompressionRotation = "rotCompress3"
        const val flagPassiveCompress = "passiveCompress"
        private val compressedRotationVector1 = SyncFlag.createFlag(flagHighCompressionRotation)
        private val compressedRotationVector2 = SyncFlag.createFlag(flagMidCompressionRotation)
        private val compressedRotationVector3 = SyncFlag.createFlag(flagLowCompressionRotation)
        private val passiveCompress = SyncFlag.createFlag(flagPassiveCompress)

        private val xField = Vector3::class.java.getDeclaredField("_x").access()
        private val yField = Vector3::class.java.getDeclaredField("_y").access()
        private val zField = Vector3::class.java.getDeclaredField("_z").access()

        private val diffValue = DiffTracked::class.java.getDeclaredField("_value")
    }

    override fun computeSizeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, state: WriteState, fieldType: Class<*>) {
        when (fieldType) {
            Vector3::class.java -> {
                val vector = field.access().get(owner) as Vector3
                val xDiff = xField.get(vector) as DiffTracked<Float>
                val yDiff = yField.get(vector) as DiffTracked<Float>
                val zDiff = zField.get(vector) as DiffTracked<Float>
                val c1 = flags.contains(compressedRotationVector1)
                val c2 = flags.contains(compressedRotationVector2)
                val c3 = flags.contains(compressedRotationVector3)
                if ((c1 and c2) or (c2 and c3) or (c1 and c3)) throw MismatchedFlagException("Cannot have more than one rotation compression flag!")

                state.registerHeader(3)
                when {
                    c1 || c2 || c3 -> {
                        val bytes =
                                when {
                                    c1 -> 1
                                    c2 -> 2
                                    else -> 3
                                }
                        state
                                .registerBytes(if (xDiff.hasChanged()) varIntSize(xDiff.value.encodeRotation(bytes).toLong()) else 0)
                                .registerBytes(if (yDiff.hasChanged()) varIntSize(yDiff.value.encodeRotation(bytes).toLong()) else 0)
                                .registerBytes(if (zDiff.hasChanged()) varIntSize(zDiff.value.encodeRotation(bytes).toLong()) else 0)
                    }
                    else -> {
                        val floatSerializer = SyncHandler.getCompatibleSerializer(Float::class.java)
                        if (xDiff.hasChanged()) floatSerializer.computeSizeExplicit(diffValue, flags, xDiff, state, Float::class.java)
                        if (yDiff.hasChanged()) floatSerializer.computeSizeExplicit(diffValue, flags, yDiff, state, Float::class.java)
                        if (zDiff.hasChanged()) floatSerializer.computeSizeExplicit(diffValue, flags, zDiff, state, Float::class.java)
                    }
                }
            }
            else -> throwInvalidType(fieldType)
        }
    }

    override fun serializeExplicit(field: Field, _flags: Array<out SyncFlag>, owner: Any?, writeBuffer: WriteBuffer, fieldType: Class<*>) {
        when (fieldType) {
            Vector3::class.java -> {
                var flags = _flags
                val vector = field.access().get(owner) as Vector3
                val c1 = flags.contains(compressedRotationVector1)
                val c2 = flags.contains(compressedRotationVector2)
                val c3 = flags.contains(compressedRotationVector3)
                val xDiff = xField.get(vector) as DiffTracked<Float>
                val yDiff = yField.get(vector) as DiffTracked<Float>
                val zDiff = zField.get(vector) as DiffTracked<Float>

                if ((c1 and c2) or (c2 and c3) or (c1 and c3)) throw MismatchedFlagException("Cannot have more than one rotation compression flag!")

                writeBuffer.writeHeader(xDiff.hasChanged())
                writeBuffer.writeHeader(yDiff.hasChanged())
                writeBuffer.writeHeader(zDiff.hasChanged())

                when {
                    c1 || c2 || c3 -> {
                        val intSerializer = SyncHandler.getCompatibleSerializer(Int::class.java)
                        val bytes =
                                when {
                                    c1 -> 1
                                    c2 -> 2
                                    else -> 3
                                }

                        val xHolder = Holder(vector.x.encodeRotation(bytes))
                        val yHolder = Holder(vector.y.encodeRotation(bytes))
                        val zHolder = Holder(vector.z.encodeRotation(bytes))

                        val nn = flags.contains(SyncFlag.NonNegative)
                        val pc = flags.contains(passiveCompress)
                        val resize = nn.toNumber() + pc.toNumber()
                        if(nn || pc){
                            var track = 0
                            flags = Array(flags.size - resize){
                                if(flags[track]== SyncFlag.NonNegative && nn){
                                    if(flags[++track]== SyncFlag.NoCompress && pc)
                                    flags[++track]
                                    else flags[track]
                                }
                                else if(flags[track]== SyncFlag.NoCompress && nn){
                                    if(flags[++track]== SyncFlag.NonNegative && pc)
                                        flags[++track]
                                    else flags[track]
                                }else flags[track]
                            }
                        }

                        if (xDiff.hasChanged()) intSerializer.serializeExplicit(Holder.valueField, flags, xHolder, writeBuffer, Int::class.java)
                        if (yDiff.hasChanged()) intSerializer.serializeExplicit(Holder.valueField, flags, yHolder, writeBuffer, Int::class.java)
                        if (zDiff.hasChanged()) intSerializer.serializeExplicit(Holder.valueField, flags, zHolder, writeBuffer, Int::class.java)
                    }
                    else -> {
                        val floatSerializer = SyncHandler.getCompatibleSerializer(Float::class.java)

                        if (xDiff.hasChanged()) floatSerializer.serializeExplicit(diffValue, _flags, xDiff, writeBuffer, Float::class.java)
                        if (yDiff.hasChanged()) floatSerializer.serializeExplicit(diffValue, _flags, yDiff, writeBuffer, Float::class.java)
                        if (zDiff.hasChanged()) floatSerializer.serializeExplicit(diffValue, _flags, zDiff, writeBuffer, Float::class.java)
                    }
                }
            }
            else -> throwInvalidType(fieldType)
        }
    }

    override fun deserializeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, readBuffer: ReadBuffer, fieldType: Class<*>) {
        when (fieldType) {
            Vector3::class.java -> {
                val vector = field.access().get(owner) as Vector3
                val c1 = flags.contains(compressedRotationVector1)
                val c2 = flags.contains(compressedRotationVector2)
                val c3 = flags.contains(compressedRotationVector3)
                val xHolder = Holder(null)
                val yHolder = Holder(null)
                val zHolder = Holder(null)
                val xDiff = xField.get(vector) as DiffTracked<Float>
                val yDiff = yField.get(vector) as DiffTracked<Float>
                val zDiff = zField.get(vector) as DiffTracked<Float>

                if ((c1 and c2) or (c2 and c3) or (c1 and c3)) throw MismatchedFlagException("Cannot have more than one rotation compression flag!")

                when {
                    c1 || c2 || c3 -> {
                        val intSerializer = SyncHandler.getCompatibleSerializer(Int::class.java)
                        val bytes = if (c1) 1 else if (c2) 2 else 3

                        if (readBuffer.readHeader()) {
                            intSerializer.deserializeExplicit(Holder.valueField, flags, xHolder, readBuffer, Int::class.java)
                            xDiff.value = (xHolder.value as Int).decodeRotation(bytes)
                            xDiff.clearChangeState()
                        }
                        if (readBuffer.readHeader()) {
                            intSerializer.deserializeExplicit(Holder.valueField, flags, yHolder, readBuffer, Int::class.java)
                            yDiff.value = (yHolder.value as Int).decodeRotation(bytes)
                            yDiff.clearChangeState()
                        }
                        if (readBuffer.readHeader()) {
                            intSerializer.deserializeExplicit(Holder.valueField, flags, zHolder, readBuffer, Int::class.java)
                            zDiff.value = (zHolder.value as Int).decodeRotation(bytes)
                            zDiff.clearChangeState()
                        }
                    }
                    else -> {
                        val floatSerializer = SyncHandler.getCompatibleSerializer(Float::class.java)

                        if (readBuffer.readHeader()){
                            floatSerializer.deserializeExplicit(diffValue, flags, xField.get(vector), readBuffer, Float::class.java)
                            xDiff.clearChangeState()
                        }
                        if (readBuffer.readHeader()){
                            floatSerializer.deserializeExplicit(diffValue, flags, yField.get(vector), readBuffer, Float::class.java)
                            yDiff.clearChangeState()
                        }
                        if (readBuffer.readHeader()){
                            floatSerializer.deserializeExplicit(diffValue, flags, zField.get(vector), readBuffer, Float::class.java)
                            zDiff.clearChangeState()
                        }
                    }
                }
            }
            else -> throwInvalidType(fieldType)
        }
    }
}