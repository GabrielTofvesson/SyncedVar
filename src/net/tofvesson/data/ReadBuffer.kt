package net.tofvesson.data

import net.tofvesson.exception.InsufficientCapacityException
import net.tofvesson.math.*
import java.nio.ByteBuffer

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ReadBuffer(state: WriteState, private val _buffer: ByteBuffer, bufferBitOffset: Int = 0) {

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
    private val maxBitOffset: Int
    private val maxByteOffset = _buffer.capacity()

    val buffer: ByteBuffer
        get() = this._buffer

    init {
        if(_buffer.capacity() - (bufferBitOffset ushr 3) - bufferBitOffset.collapseLowerByte() < state.computeBitFieldOffset())
            throw InsufficientCapacityException()
        byteIndex = readPackedInt(true)
        maxBitOffset = bufferBitOffset + byteIndex*8
    }


    private fun readBit(head: Boolean): Boolean {
        if(head && headerIndex >= maxHeaderSize)
            throw IndexOutOfBoundsException("Attempt to read more headers than available space permits!")

        val index = (if(head) headerIndex else bitOffset) ushr 3
        val shift = (if(head) headerIndex else bitOffset) and 7
        if(head) ++headerIndex
        else ++bitOffset
        return (_buffer[index].toInt() and (1 shl shift)) != 0
    }

    fun readHeader() = readBit(true)
    fun readBit() = readBit(false)

    fun readByte(): Byte {
        //doBoundaryCheck(1)
        return _buffer.get(byteIndex++)
    }

    fun readShort(): Short {
        //doBoundaryCheck(2)
        val res = _buffer.getShort(byteIndex)
        byteIndex += 2
        return res
    }

    fun readInt(): Int {
        //doBoundaryCheck(4)
        val res = _buffer.getInt(byteIndex)
        byteIndex += 4
        return res
    }

    fun readLong(): Long {
        //doBoundaryCheck(8)
        val res = _buffer.getLong(byteIndex)
        byteIndex += 8
        return res
    }

    fun readFloat(): Float {
        //doBoundaryCheck(4)
        val res = _buffer.getFloat(byteIndex)
        byteIndex += 4
        return res
    }

    fun readDouble(): Double {
        //doBoundaryCheck(8)
        val res = _buffer.getDouble(byteIndex)
        byteIndex += 8
        return res
    }

    fun readPackedShort(noZigZag: Boolean = false) = readPackedLong(noZigZag).toShort()
    fun readPackedInt(noZigZag: Boolean = false) = readPackedLong(noZigZag).toInt()
    fun readPackedLong(noZigZag: Boolean = false): Long {
        //doBoundaryCheck(1)
        val header: Long = buffer[byteIndex++].toLong() and 0xFF
        if (header <= 240L) return if(noZigZag) header else zigZagDecode(header)
        if (header <= 248L){
            //doBoundaryCheck(2)
            val res = 240L + ((header - 241L).shl(8)) + (buffer[byteIndex++].toLong() and 0xFF)
            return if(noZigZag) res else zigZagDecode(res)
        }
        if (header == 249L){
            //doBoundaryCheck(3)
            val res = 2288 + ((buffer[byteIndex++].toLong() and 0xFF).shl(8)) + (buffer[byteIndex++].toLong() and 0xFF)
            return if(noZigZag) res else zigZagDecode(res)
        }
        val hdr = header - 247
        //doBoundaryCheck(hdr.toInt())
        var res = (buffer[byteIndex++].toLong() and 0xFF).or(((buffer[byteIndex++].toLong() and 0xFF).shl(8)).or((buffer[byteIndex++].toLong() and 0xFF).shl(16)))
        var cmp = 2
        while (hdr > ++cmp)
            res = res.or((buffer[byteIndex++].toLong() and 0xFF).shl(cmp.shl(3)))

        return if(noZigZag) res else zigZagDecode(res)
    }

    fun readPackedFloat(noSwapEndian: Boolean = false): Float {
        val readVal = readPackedInt(true)
        return if(noSwapEndian) intToFloat(readVal) else intToFloat(swapEndian(readVal))
    }

    fun readPackedDouble(noSwapEndian: Boolean = false): Double {
        val readVal = readPackedLong(true)
        return if(noSwapEndian) longToDouble(readVal) else longToDouble(swapEndian(readVal))
    }

    private fun doBoundaryCheck(byteCount: Int) {
        if(byteIndex + byteCount > maxByteOffset)
            throw IndexOutOfBoundsException("Attempt to read value past maximum range!")
    }
}