package net.tofvesson.data

import net.tofvesson.math.*
import java.nio.ByteBuffer

class RBuffer(val buffer: ByteBuffer, bitOffset: Long)
{
    /*
     * Backing fields for indices 
     */
    private var bits = 0L
    private var bytes = 0

    init {
        bytes = (readPackedMisaligned((bitOffset ushr 3).toInt(), (bitOffset and 7).toInt()).toInt() + bitOffset.bitIndexToBytes()).toInt()
        bits += (varIntSize(bytes.toLong()) * 8) + bitOffset
    }

    fun readBit() = readBitAt(bits++)

    fun readByte() = buffer.get(bytes++)

    fun readShort(): Short {
        val result = buffer.getShort(bytes)
        bytes += 2

        return result
    }

    fun readInt(): Int {
        val result = buffer.getInt(bytes)
        bytes += 4

        return result
    }

    fun readLong(): Long {
        val result = buffer.getLong(bytes)
        bytes += 8

        return result
    }

    fun readFloat(): Float {
        val result = buffer.getFloat(bytes)
        bytes += 4

        return result
    }

    fun readDouble(): Double {
        val result = buffer.getDouble(bytes)
        bytes += 8

        return result
    }


    fun readPackedShort(noZigZag: Boolean = false) = readPackedLong(noZigZag).toShort()
    fun readPackedInt(noZigZag: Boolean = false) = readPackedLong(noZigZag).toInt()
    fun readPackedLong(noZigZag: Boolean = false): Long {
        //doBoundaryCheck(1)
        val header: Long = buffer[bytes++].toLong() and 0xFF
        if (header <= 240L) return if(noZigZag) header else zigZagDecode(header)
        if (header <= 248L){
            //doBoundaryCheck(2)
            val res = 240L + ((header - 241L).shl(8)) + (buffer[bytes++].toLong() and 0xFF)
            return if(noZigZag) res else zigZagDecode(res)
        }
        if (header == 249L){
            //doBoundaryCheck(3)
            val res = 2288 + ((buffer[bytes++].toLong() and 0xFF).shl(8)) + (buffer[bytes++].toLong() and 0xFF)
            return if(noZigZag) res else zigZagDecode(res)
        }
        val hdr = header - 247
        //doBoundaryCheck(hdr.toInt())
        var res = (buffer[bytes++].toLong() and 0xFF).or(((buffer[bytes++].toLong() and 0xFF).shl(8)).or((buffer[bytes++].toLong() and 0xFF).shl(16)))
        var cmp = 2
        while (hdr > ++cmp)
            res = res.or((buffer[bytes++].toLong() and 0xFF).shl(cmp.shl(3)))

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


    private fun readMisaligned(index: Int, shift: Int) =
            ((buffer[index].toInt() and 0xFF ushr shift) or (buffer[index + 1].toInt() and 0xFF shl (8 - shift))).toByte()

    private fun readPackedMisaligned(index: Int, shift: Int): Long {
        var idx = index
        val header: Long = readMisaligned(idx++, shift).toLong() and 0xFF
        if (header <= 240L) return header
        if (header <= 248L){
            return 240L + ((header - 241L).shl(8)) + (readMisaligned(idx, shift).toLong() and 0xFF)
        }
        if (header == 249L){
            return 2288 + ((readMisaligned(idx++, shift).toLong() and 0xFF).shl(8)) + (readMisaligned(idx, shift).toLong() and 0xFF)
        }
        val hdr = header - 247
        //doBoundaryCheck(hdr.toInt())
        var res = (readMisaligned(idx++, shift).toLong() and 0xFF).or(((readMisaligned(idx++, shift).toLong() and 0xFF).shl(8)).or((readMisaligned(idx++, shift).toLong() and 0xFF).shl(16)))
        var cmp = 2
        while (hdr > ++cmp)
            res = res.or((readMisaligned(idx++, shift).toLong() and 0xFF).shl(cmp.shl(3)))

        return res
    }

    private fun readBitAt(bitIndex: Long) =
            (buffer[(bitIndex ushr 3).toInt()].toInt() ushr (bitIndex and 7).toInt()) and 1 != 0

    private fun Long.bitIndexToBytes() = (this ushr 3) + ((this or (this ushr 1) or (this ushr 2)) and 1)
}