package net.tofvesson.data

import net.tofvesson.math.collapseLowerByte

data class WriteState(private var _bytes: Int, private var _bits: Int, private var _header: Int){
    var bytes: Int = _bytes
        private set
    var bits: Int = _bits
        private set

    fun registerBytes(bytes: Int): WriteState { this.bytes  += bytes;  return this }
    fun registerBits(bits: Int) : WriteState { this.bits   += bits;   return this }

    fun computeRequiredBytes(additionalBytes: Int = 0, additionalBits: Int = 0) =
            bytes + additionalBytes + computeBitFieldOffset(additionalBits)
    fun computeBitFieldOffset(additionalBits: Int = 0) = roundUpBitsToBytes(bits + additionalBits)

    private fun roundUpBitsToBytes(bits: Int) = (bits ushr 3) + bits.collapseLowerByte()
}