package net.tofvesson.networking

import java.nio.ByteBuffer

fun varIntSize(value: Long): Int =
        when {
            value <= 240 -> 1
            value <= 2287 -> 2
            value <= 67823 -> 3
            value <= 16777215 -> 4
            value <= 4294967295 -> 5
            value <= 1099511627775 -> 6
            value <= 281474976710655 -> 7
            value <= 72057594037927935 -> 8
            else -> 9
        }

fun writeVarInt(buffer: ByteArray, offset: Int, value: Long){
    when(value) {
        in 0..240 -> buffer[offset] = value.toByte()
        in 241..2287 -> {
            buffer[offset] = (((value - 240) shr 8) + 241).toByte()
            buffer[offset+1] = (value - 240).toByte()
        }
        in 2288..67823 -> {
            buffer[offset] = 249.toByte()
            buffer[offset+1] = ((value - 2288) shr 8).toByte()
            buffer[offset+2] = (value - 2288).toByte()
        }
        else -> {
            var header = 255
            var match = 0x00FF_FFFF_FFFF_FFFFL
            while (value in 67824..match) {
                --header
                match = match shr 8
            }
            buffer[offset] = header.toByte()
            val max = header - 247
            for (i in 0..(max-1)) buffer[offset+i+1] = (value shr (i shl 3)).toByte()
        }
    }
}

fun writeVarInt(buffer: ByteBuffer, offset: Int, value: Long){
    when (value) {
        in 0..240 -> buffer.put(offset, value.toByte())
        in 0..2287 -> {
            buffer.put(offset, (((value - 240) shr 8) + 241).toByte())
            buffer.put(offset+1, (value - 240).toByte())
        }
        in 0..67823 -> {
            buffer.put(offset, 249.toByte())
            buffer.put(offset+1, ((value - 2288) shr 8).toByte())
            buffer.put(offset+2, (value - 2288).toByte())
        }
        else -> {
            var header = 255
            var match = 0x00FF_FFFF_FFFF_FFFFL
            while (value in 67824..match) {
                --header
                match = match shr 8
            }
            buffer.put(offset, header.toByte())
            val max = header - 247
            for (i in 0..(max-1)) buffer.put(offset+i+1, (value shr (i shl 3)).toByte())
        }
    }
}

fun writeInt(buffer: ByteArray, offset: Int, value: Long, bytes: Int){
    for(i in 0..(bytes-1))
        buffer[offset+i] = (value shr (8*i)).toByte()
}

fun writeInt(buffer: ByteBuffer, offset: Int, value: Long, bytes: Int){
    for(i in 0..(bytes-1))
        buffer.put(offset+i, (value shr (8*i)).toByte())
}

fun readVarInt(buffer: ByteArray, offset: Int): Long {
    var off = offset
    val header: Long = buffer[off++].toLong() and 0xFF
    if (header <= 240L) return header
    if (header <= 248L) return 240L + ((header - 241L).shl(8)) + (buffer[off].toLong() and 0xFF)
    if (header == 249L) return 2288 + ((buffer[off++].toLong() and 0xFF).shl(8)) + (buffer[off].toLong() and 0xFF)
    var res = (buffer[off++].toLong() and 0xFF).or(((buffer[off++].toLong() and 0xFF).shl(8)).or((buffer[off++].toLong() and 0xFF).shl(16)))
    var cmp = 2
    val hdr = header - 247
    while (hdr > ++cmp) res = res.or((buffer[off++].toLong() and 0xFF).shl(cmp.shl(3)))
    return res
}

fun readVarInt(buffer: ByteBuffer, offset: Int): Long {
    var off = offset
    val header: Long = (buffer[off++].toLong() and 0xFF)
    if (header <= 240L) return header
    if (header <= 248L) return 240L + ((header - 241L).shl(8)) + (buffer[off].toLong() and 0xFF)
    if (header == 249L) return 2288 + ((buffer[off++].toLong() and 0xFF).shl(8)) + (buffer[off].toLong() and 0xFF)
    var res = (buffer[off++].toLong() and 0xFF).or(((buffer[off++].toLong() and 0xFF).shl(8)).or((buffer[off++].toLong() and 0xFF).shl(16)))
    var cmp = 2
    val hdr = header - 247
    while (hdr > ++cmp) res = res.or((buffer[off++].toLong() and 0xFF).shl(cmp.shl(3)))
    return res
}

private val converter = ByteBuffer.allocateDirect(8)

fun floatToInt(value: Float): Int =
    synchronized(converter){
        converter.putFloat(0, value)
        return@synchronized converter.getInt(0)
    }

fun doubleToLong(value: Double): Long =
        synchronized(converter){
            converter.putDouble(0, value)
            return@synchronized converter.getLong(0)
        }

fun intToFloat(value: Int): Float =
        synchronized(converter){
            converter.putInt(0, value)
            return@synchronized converter.getFloat(0)
        }

fun longToDouble(value: Long): Double =
        synchronized(converter){
            converter.putLong(0, value)
            return@synchronized converter.getDouble(0)
        }

fun swapEndian(value: Short) = ((value.toInt() shl 8) or ((value.toInt() shr 8) and 255)).toShort()
fun swapEndian(value: Int) =
                ((value shr 24) and 0xFF) or
                ((value shr 8) and 0xFF00) or
                ((value shl 24) and -16777216) or
                ((value shl 8) and 0xFF0000)
fun swapEndian(value: Long) =
        ((value shr 56) and 0xFFL) or
                ((value shr 40) and 0xFF00L) or
                ((value shr 24) and 0xFF0000L) or
                ((value shr 8) and 0xFF000000L) or
                ((value shl 56) and -72057594037927936L) or
                ((value shl 40) and 0xFF000000000000L) or
                ((value shl 24) and 0xFF0000000000L) or
                ((value shl 8) and 0xFF00000000L)

fun bitConvert(value: Int): Long = value.toLong() and 0xFFFFFFFFL

fun zigZagEncode(value: Long): Long = (value shl 1) xor (value shr 63)
fun zigZagDecode(value: Long): Long = (value shr 1) xor ((value shl 63) shr 63)