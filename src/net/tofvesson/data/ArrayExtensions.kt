package net.tofvesson.data

fun ByteArray.readBits(index: Int, bitCount: Int, bitOffset: Int = 0): Int {
    var result = 0
    for(i in 0 until bitCount)
        result = result or readBit(index, bitOffset+i)
    return result
}

fun ByteArray.readBit(index: Int, bitOffset: Int = 0) = (this[index + (bitOffset ushr 3)].toInt() ushr (bitOffset and 7)) and 1