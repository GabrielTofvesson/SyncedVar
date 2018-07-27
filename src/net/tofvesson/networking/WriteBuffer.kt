package net.tofvesson.networking

import net.tofvesson.math.collapseLowerByte
import net.tofvesson.math.toNumber
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.or

@Suppress("MemberVisibilityCanBePrivate", "unused")
class WriteBuffer(state: WriteState, _buffer: ByteBuffer? = null, bufferBitOffset: Int = 0) {
    /*
    Buffer layout:
    {BufferBitOffset}[Header][Bits]{BitOffset}[Bytes]
    BufferBitOffset is optional and usually set to 0
    BitOffset is between 0 and 7 bits long (to realign data to the next free byte)
     */
    private var headerIndex = bufferBitOffset
    private var bitOffset = bufferBitOffset + state.header
    private var byteIndex = state.computeBitFieldOffset(bufferBitOffset)

    // Maximum size for the above values
    private val maxHeaderSize = bufferBitOffset + state.header
    private val maxBitOffset = bufferBitOffset + state.header + state.bits
    private val maxByteOffset = state.computeRequiredBytes(0, bufferBitOffset)
    
    val buffer: ByteBuffer

    init{
        buffer = _buffer ?: ByteBuffer.allocate(state.computeRequiredBytes() + varIntSize(byteIndex.toLong()))
        if(buffer.capacity() + (bufferBitOffset ushr 3) + bufferBitOffset.collapseLowerByte() + varIntSize(byteIndex.toLong()) < state.computeRequiredBytes())
            throw InsufficientCapacityException()
        writePackedInt(byteIndex + varIntSize(byteIndex.toLong()), true)
    }

    private fun writeBit(bit: Boolean, head: Boolean){
        if((head && headerIndex >= maxHeaderSize) || (!head && bitOffset >= maxBitOffset))
            throw IndexOutOfBoundsException("Attempt to write more ${if(head)"headers" else "bits"} than available space permits!")

        val index = (if(head) headerIndex else bitOffset) ushr 3
        val shift = (if(head) headerIndex else bitOffset) and 7
        buffer.put(index, (buffer[index] and (1 shl shift).inv().toByte()) or (bit.toNumber() shl shift).toByte())
        if(head) ++headerIndex
        else ++bitOffset
    }

    fun writeHeader(bit: Boolean): WriteBuffer {
        writeBit(bit, true)
        return this
    }

    fun writeBit(bit: Boolean): WriteBuffer {
        writeBit(bit, false)
        return this
    }
    
    fun writeByte(value: Byte): WriteBuffer {
        doBoundaryCheck(1)
        buffer.put(byteIndex, value)
        ++byteIndex
        return this
    }

    fun writeShort(value: Short): WriteBuffer {
        doBoundaryCheck(2)
        buffer.putShort(byteIndex, value)
        byteIndex += 2
        return this
    }

    fun writeInt(value: Int): WriteBuffer {
        doBoundaryCheck(4)
        buffer.putInt(byteIndex, value)
        byteIndex += 4
        return this
    }

    fun writeLong(value: Long): WriteBuffer {
        doBoundaryCheck(8)
        buffer.putLong(byteIndex, value)
        byteIndex += 8
        return this
    }

    fun writeFloat(value: Float): WriteBuffer {
        doBoundaryCheck(4)
        buffer.putFloat(byteIndex, value)
        byteIndex += 4
        return this
    }

    fun writeDouble(value: Double): WriteBuffer {
        doBoundaryCheck(8)
        buffer.putDouble(byteIndex, value)
        byteIndex += 8
        return this
    }
    
    fun writePackedShort(value: Short, noZigZag: Boolean = false) = writePackedLong(value.toLong(), noZigZag)
    fun writePackedInt(value: Int, noZigZag: Boolean = false) = writePackedLong(value.toLong(), noZigZag)
    fun writePackedLong(value: Long, noZigZag: Boolean = false): WriteBuffer {
        val toWrite = if(noZigZag) value else zigZagEncode(value)
        val size = varIntSize(toWrite)

        doBoundaryCheck(size)

        when(toWrite) {
            in 0..240 -> buffer.put(byteIndex, toWrite.toByte())
            in 241..2287 -> {
                buffer.put(byteIndex, (((toWrite - 240) shr 8) + 241).toByte())
                buffer.put(byteIndex + 1, (toWrite - 240).toByte())
            }
            in 2288..67823 -> {
                buffer.put(byteIndex, 249.toByte())
                buffer.put(byteIndex + 1, ((toWrite - 2288) shr 8).toByte())
                buffer.put(byteIndex + 2, (toWrite - 2288).toByte())
            }
            else -> {
                var header = 255
                var match = 0x00FF_FFFF_FFFF_FFFFL
                while (toWrite in 67824..match) {
                    --header
                    match = match shr 8
                }
                buffer.put(byteIndex, header.toByte())
                val max = header - 247
                for (i in 0 until max)
                    buffer.put(byteIndex + 1 + i, (toWrite shr (i shl 3)).toByte())
            }
        }

        byteIndex += size

        return this
    }

    fun writePackedFloat(value: Float, noSwapEndian: Boolean = false): WriteBuffer {
        val write =
                if(noSwapEndian) bitConvert(floatToInt(value))
                else bitConvert(swapEndian(floatToInt(value)))
        return writePackedLong(write, true)
    }

    fun writePackedDouble(value: Double, noSwapEndian: Boolean = false): WriteBuffer {
        val write =
                if(noSwapEndian) doubleToLong(value)
                else swapEndian(doubleToLong(value))
        return writePackedLong(write, true)
    }
    
    private fun doBoundaryCheck(byteCount: Int) {
        if(byteIndex + byteCount > maxByteOffset)
            throw IndexOutOfBoundsException("Attempt to write value past maximum range!")
    }
}