package net.tofvesson.data

import net.tofvesson.math.*
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

class WBuffer(_buffer: ByteBuffer?, bufferBitOffset: Long, private val maxima: WriteState)
{
    /*
     * Base indices
     * Header base comes immediately after a special (not necessarily byte-aligned) byteBase index header
     */
    /**
     * Base bit index for writing metadata
     */
    val metaBase = bufferBitOffset
    val bitsBase = metaBase + (varIntSize((maxima.bits).toLong().bitIndexToBytes()) * 8)
    val bytesBase = (bitsBase + maxima.bits).bitIndexToBytes()

    /*
     * Backing fields for indices
     */
    private var bits = 0L
    private var bytes = 0

    /*
     * Indices
     */
    var bitIndex
        get() = bitsBase + bits
        set(value) {
            if (value < bitsBase || value > (bitsBase + maxima.bits))
                throw IndexOutOfBoundsException("Attempt to index bits beyond acceptable range")

            bits = value - bitsBase
        }

    var byteIndex
        get() = (bytesBase + bytes).toInt()
        set(value) {
            if (value < bytesBase || value > maxima.bytes + bytesBase + 1)
                throw IndexOutOfBoundsException("Attempt to index bytes beyond acceptable range")

            bytes = (value - bytesBase).toInt()
        }


    val buffer: ByteBuffer

    init {
        /*
         * If given buffer is null, allocate a new buffer
         * If given buffer is too small, allocate a new buffer and copy given buffer to the new buffer
         * If given buffer is large enough, simply assign buffer field to said buffer
         */
        when {
            _buffer == null -> buffer = ByteBuffer.allocate((bytesBase + maxima.bytes).toInt())
            _buffer.capacity() < bytesBase + maxima.bytes -> {
                buffer = ByteBuffer.allocate((bytesBase + maxima.bytes).toInt())
                buffer.put(_buffer)
            }
            else -> buffer = _buffer
        }

        writePackedMisaligned((bufferBitOffset ushr 3).toInt(), (bufferBitOffset and 7).toInt(), bytesBase)
    }

    fun writeBit(value: Boolean): WBuffer {
        ensureBitBounds()

        writeBitAt(bitIndex, value)
        ++bits

        return this
    }

    fun writeByte(value: Byte): WBuffer {
        ensureByteBounds(1)

        buffer.put(byteIndex, value)
        ++bytes

        return this
    }

    fun writeShort(value: Short): WBuffer {
        ensureByteBounds(2)

        buffer.putShort(byteIndex, value)
        bytes += 2

        return this
    }

    fun writeInt(value: Int): WBuffer {
        ensureByteBounds(4)

        buffer.putInt(byteIndex, value)
        bytes += 4

        return this
    }

    fun writeLong(value: Long): WBuffer {
        ensureByteBounds(8)

        buffer.putLong(byteIndex, value)
        bytes += 8

        return this
    }

    fun writeFloat(value: Float): WBuffer {
        ensureByteBounds(4)

        buffer.putFloat(byteIndex, value)
        bytes += 4

        return this
    }

    fun writeDouble(value: Double): WBuffer {
        ensureByteBounds(8)

        buffer.putDouble(byteIndex, value)
        bytes += 8

        return this
    }

    fun writePackedShort(value: Short, noZigZag: Boolean = false) = writePackedLong(value.toLong(), noZigZag)
    fun writePackedInt(value: Int, noZigZag: Boolean = false) = writePackedLong(value.toLong(), noZigZag)
    fun writePackedLong(value: Long, noZigZag: Boolean = false): WBuffer {
        val toWrite = if(noZigZag) value else zigZagEncode(value)
        val size = varIntSize(toWrite)

        ensureByteBounds(size.toLong())

        when(toWrite) {
            in 0..240 -> buffer.put(byteIndex, toWrite.toByte())
            in 241..2287 -> {
                buffer.put(byteIndex, (((toWrite - 240) ushr 8) + 241).toByte())
                buffer.put(byteIndex + 1, (toWrite - 240).toByte())
            }
            in 2288..67823 -> {
                buffer.put(byteIndex, 249.toByte())
                buffer.put(byteIndex + 1, ((toWrite - 2288) ushr 8).toByte())
                buffer.put(byteIndex + 2, (toWrite - 2288).toByte())
            }
            else -> {
                var header = 255
                var match = 0x00FF_FFFF_FFFF_FFFFL
                while (toWrite in 67824..match) {
                    --header
                    match = match ushr 8
                }
                buffer.put(byteIndex, header.toByte())
                val max = header - 247
                for (i in 0 until max)
                    buffer.put(byteIndex + 1 + i, (toWrite ushr (i shl 3)).toByte())
            }
        }

        bytes += size

        return this
    }

    fun writePackedFloat(value: Float, noSwapEndian: Boolean = false): WBuffer {
        val write =
                if(noSwapEndian) bitConvert(floatToInt(value))
                else bitConvert(swapEndian(floatToInt(value)))
        return writePackedLong(write, true)
    }

    fun writePackedDouble(value: Double, noSwapEndian: Boolean = false): WBuffer {
        val write =
                if(noSwapEndian) doubleToLong(value)
                else swapEndian(doubleToLong(value))
        return writePackedLong(write, true)
    }

    private fun writeMisaligned(index: Int, shift: Int, value: Byte) {
        if(shift == 0)
        {
            buffer.put(index, value)
            return
        }

        val byte = buffer[index] and (-1 shr (8 - shift)).toByte().inv()
        val nextByte = buffer[index + 1] and (-1 shl shift).toByte()

        buffer.put(index, byte or (value.toInt() shl (8 - shift) and 0xFF).toByte())
        buffer.put(index + 1, nextByte or (value.toInt() and 0xFF ushr shift).toByte())
    }

    private fun writePackedMisaligned(index: Int, shift: Int, value: Long) {
        when (value) {
            in 0..240 -> writeMisaligned(index, shift, value.toByte())
            in 241..2287 -> {
                writeMisaligned(index, shift, (((value - 240) ushr 8) + 241).toByte())
                writeMisaligned(index + 1, shift, (value - 240).toByte())
            }
            in 2288..67823 -> {
                writeMisaligned(index, shift, 249.toByte())
                writeMisaligned(index + 1, shift, ((value - 2288) ushr 8).toByte())
                writeMisaligned(index + 2, shift, (value - 2288).toByte())
            }
            else -> {
                var header = 255
                var match = 0x00FF_FFFF_FFFF_FFFFL
                while (value in 67824..match) {
                    --header
                    match = match ushr 8
                }
                writeMisaligned(index, shift, header.toByte())
                val max = header - 247
                for (i in 0 until max)
                    writeMisaligned(index + 1 + i, shift, (value ushr (i shl 3)).toByte())
            }
        }
    }


    private fun writeBitAt(bitIndex: Long, value: Boolean)
    {
        val byteIndex = (bitIndex ushr 3).toInt()
        val byteShift = (bitIndex and 7).toInt()

        val byte = buffer[byteIndex]

        if (value)
            buffer.put(byteIndex, byte or (1 shl byteShift).toByte())
        else
            buffer.put(byteIndex, byte and (1 shl byteShift).toByte().inv())
    }

    private fun ensureBitBounds() = ensureBounds(bits, 1, maxima.bits.toLong())
    private fun ensureByteBounds(count: Long) = ensureBounds(bytes.toLong(), count, maxima.bytes.toLong())
    private fun ensureBounds(index: Long, increment: Long, maximum: Long)
    {
        if (index + increment > maximum)
            throw IndexOutOfBoundsException("Attempt to write past boundary!")
    }

    private fun Long.bitIndexToBytes() = (this ushr 3) + ((this or (this ushr 1) or (this ushr 2)) and 1)
}