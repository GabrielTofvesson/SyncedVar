package net.tofvesson.networking

import java.lang.reflect.Field
import java.nio.ByteBuffer
import kotlin.experimental.and

class SyncHandler {
    private val toSync: ArrayList<Pair<Any, Int>> = ArrayList()

    fun registerSyncObject(value: Any){
        if(!toSync.contains(value)) toSync.add(Pair(value, getBooleanFieldCount(value::class.java)))
    }

    fun unregisterSyncObject(value: Any){
        toSync.remove(value)
    }

    fun serialize(): ByteArray{
        val headerBits = computeBitHeaderCount()
        val headerSize = (headerBits shr 3) + (if((headerBits and 7) != 0) 1 else 0)
        var headerIndex = 0
        var dataIndex = headerSize
        var totalSize = headerSize
        for((entry, _) in toSync)
            totalSize += if(entry is Class<*>) computeClassSize(entry) else computeObjectSize(entry)

        val buffer = ByteArray(totalSize)
        for((entry, _) in toSync){
            val result =
                    if(entry is Class<*>) readClass(entry, buffer, dataIndex, headerIndex)
                    else readObject(entry, buffer, dataIndex, headerIndex)
            dataIndex = result.first
            headerIndex = result.second
        }
        return buffer
    }

    fun deserialize(syncData: ByteArray){
        val headerBits = computeBitHeaderCount()
        val headerSize = (headerBits shr 3) + (if((headerBits and 7) != 0) 1 else 0)
        var headerIndex = 0
        var dataIndex = headerSize
        var totalSize = headerSize
        for((entry, _) in toSync){
            val result =
                    if(entry is Class<*>) writeClass(entry, syncData, dataIndex, headerIndex)
                    else writeObject(entry, syncData, dataIndex, headerIndex)
            dataIndex = result.first
            headerIndex = result.second
        }
    }

    fun generateMismatchCheck(): ByteArray {
        val bitCount = computeBitHeaderCount()
        val outBuffer = ByteArray(varIntSize(bitCount.toLong()) + varIntSize(toSync.size.toLong()))
        writeVarInt(outBuffer, 0, bitCount.toLong())
        writeVarInt(outBuffer, varIntSize(bitCount.toLong()), toSync.size.toLong())
        return outBuffer
    }

    fun doMismatchCheck(check: ByteArray): Boolean {
        val mismatchCheck = generateMismatchCheck()
        if(mismatchCheck.size != check.size) return false
        for(index in mismatchCheck.indices)
            if(mismatchCheck[index]!=check[index])
                return false
        return true
    }

    private fun computeBitHeaderCount(): Int {
        var bitCount = 0
        for((_, bits) in toSync)
            bitCount += bits
        return bitCount
    }

    private fun readObject(value: Any, buffer: ByteArray, offset: Int, bitOffset: Int) = readType(value.javaClass, value, buffer, offset, bitOffset)
    private fun readClass(value: Class<*>, buffer: ByteArray, offset: Int, bitOffset: Int) = readType(value, null, buffer, offset, bitOffset)
    private fun readType(type: Class<*>, value: Any?, buffer: ByteArray, offset: Int, bitOffset: Int): Pair<Int, Int> {
        val byteBuffer = ByteBuffer.wrap(buffer)
        var localOffset = offset
        var localBitOffset = bitOffset
        for(field in type.declaredFields){
            if((value == null && field.modifiers and 8 == 0) || (value != null && field.modifiers and 8 != 0)) continue
            val annotation = field.getAnnotation(SyncedVar::class.java)
            if(annotation != null){
                if(field.type!=Boolean::class.java) localOffset += readValue(field, annotation, value, byteBuffer, localOffset)
                else writeBit(field.getBoolean(value), buffer, localBitOffset++)
            }
        }
        return Pair(localOffset, localBitOffset)
    }

    private fun writeObject(value: Any, buffer: ByteArray, offset: Int, bitOffset: Int) = writeType(value.javaClass, value, buffer, offset, bitOffset)
    private fun writeClass(value: Class<*>, buffer: ByteArray, offset: Int, bitOffset: Int) = writeType(value, null, buffer, offset, bitOffset)
    private fun writeType(type: Class<*>, value: Any?, buffer: ByteArray, offset: Int, bitOffset: Int): Pair<Int, Int> {
        val byteBuffer = ByteBuffer.wrap(buffer)
        var localOffset = offset
        var localBitOffset = bitOffset
        for(field in type.declaredFields){
            if((value == null && field.modifiers and 8 == 0) || (value != null && field.modifiers and 8 != 0)) continue
            val annotation = field.getAnnotation(SyncedVar::class.java)
            if(annotation != null){
                if(field.type!=Boolean::class.java) localOffset += writeValue(field, annotation, value, byteBuffer, localOffset)
                else field.setBoolean(value, readBit(buffer, localBitOffset++))
            }
        }
        return Pair(localOffset, localBitOffset)
    }

    companion object {
        private fun getBooleanFieldCount(type: Class<*>): Int {
            var count = 0
            for(field in type.declaredFields)
                if(field.getAnnotation(SyncedVar::class.java)!=null && field.type==Boolean::class.java)
                    ++count
            return count
        }
        private fun writeBit(bit: Boolean, buffer: ByteArray, index: Int){
            buffer[index shr 3] = buffer[index shr 3] and (1 shl (index and 7)).toByte()
        }
        private fun readBit(buffer: ByteArray, index: Int): Boolean = buffer[index shr 8].toInt() and (1 shl (index and 7)) != 0

        private fun computeObjectSize(value: Any) = computeTypeSize(value.javaClass, value)
        private fun computeClassSize(value: Class<*>) = computeTypeSize(value, null)
        private fun computeTypeSize(type: Class<*>, value: Any?): Int{
            var byteSize = 0
            for(field in type.declaredFields){
                if((value == null && field.modifiers and 8 == 0) || (value != null && field.modifiers and 8 != 0)) continue
                val annotation = field.getAnnotation(SyncedVar::class.java)
                field.trySetAccessible()
                if(annotation!=null) byteSize += computeDataSize(field, annotation, value)
            }
            return byteSize
        }
        private fun computeDataSize(field: Field, annotation: SyncedVar, value: Any?): Int =
                when(field.type){
                    Byte::class.java -> 1
                    Short::class.java ->
                        if(annotation.noCompress) 2
                        else varIntSize(
                                if(annotation.nonNegative) field.getShort(value).toLong()
                                else zigZagEncode(field.getShort(value).toLong())
                        )
                    Int::class.java ->
                        if(annotation.noCompress) 4
                        else varIntSize(
                                if(annotation.nonNegative) field.getInt(value).toLong()
                                else zigZagEncode(field.getInt(value).toLong())
                        )
                    Long::class.java ->
                        if(annotation.noCompress) 8
                        else varIntSize(
                                if(annotation.nonNegative) field.getLong(value)
                                else zigZagEncode(field.getLong(value))
                        )
                    Float::class.java ->
                        if(annotation.noCompress) 4
                        else varIntSize(
                                if(annotation.floatEndianSwap) bitConvert(swapEndian(floatToInt(field.getFloat(value))))
                                else bitConvert(floatToInt(field.getFloat(value)))
                        )
                    Double::class.java ->
                        if(annotation.noCompress) 8
                        else varIntSize(
                                if(annotation.floatEndianSwap) swapEndian(doubleToLong(field.getDouble(value)))
                                else doubleToLong(field.getDouble(value))
                        )
                    else -> 0
                }

        private fun readValue(field: Field, annotation: SyncedVar, value: Any?, buffer: ByteBuffer, offset: Int): Int =
            when(field.type){
                Byte::class.java ->{
                    buffer.put(offset, field.getByte(value))
                    1
                }
                Short::class.java ->
                    if(annotation.noCompress){
                        buffer.putShort(offset, field.getShort(value))
                        2
                    }
                    else {
                        val rawValue =
                                if(annotation.nonNegative) field.getShort(value).toLong()
                                else zigZagEncode(field.getShort(value).toLong())
                        writeVarInt(buffer, offset, rawValue)
                        varIntSize(rawValue)
                    }
                Int::class.java ->
                    if(annotation.noCompress){
                        buffer.putInt(offset, field.getInt(value))
                        4
                    }
                    else {
                        val rawValue =
                                if(annotation.nonNegative) field.getInt(value).toLong()
                                else zigZagEncode(field.getInt(value).toLong())
                        writeVarInt(buffer, offset, rawValue)
                        varIntSize(rawValue)
                    }
                Long::class.java ->
                    if(annotation.noCompress){
                        buffer.putLong(offset, field.getLong(value))
                        8
                    }
                    else {
                        val rawValue =
                                if(annotation.nonNegative) field.getLong(value)
                                else zigZagEncode(field.getLong(value))
                        writeVarInt(buffer, offset, rawValue)
                        varIntSize(rawValue)
                    }
                Float::class.java ->
                    if(annotation.noCompress){
                        buffer.putFloat(offset, field.getFloat(value))
                        4
                    }
                    else{
                        val rawValue =
                                if(annotation.floatEndianSwap) bitConvert(swapEndian(floatToInt(field.getFloat(value))))
                                else bitConvert(floatToInt(field.getFloat(value)))
                        writeVarInt(buffer, offset, rawValue)
                        varIntSize(rawValue)
                    }
                Double::class.java ->
                    if(annotation.noCompress){
                        buffer.putDouble(offset, field.getDouble(value))
                        8
                    }
                    else{
                        val rawValue =
                                if(annotation.floatEndianSwap) swapEndian(doubleToLong(field.getDouble(value)))
                                else doubleToLong(field.getDouble(value))
                        writeVarInt(buffer, offset, rawValue)
                        varIntSize(rawValue)
                    }
                else -> 0
            }

        private fun writeValue(field: Field, annotation: SyncedVar, value: Any?, buffer: ByteBuffer, offset: Int): Int =
                when(field.type){
                    Byte::class.java ->{
                        field.setByte(value, buffer.get(offset))
                        1
                    }
                    Short::class.java ->
                        if(annotation.noCompress){
                            field.setShort(value, buffer.getShort(offset))
                            2
                        }
                        else {
                            val rawValue =
                                    if(annotation.nonNegative) readVarInt(buffer, offset)
                                    else zigZagDecode(readVarInt(buffer, offset))
                            field.setShort(value, rawValue.toShort())
                            varIntSize(rawValue)
                        }
                    Int::class.java ->
                        if(annotation.noCompress){
                            field.setInt(value, buffer.getInt(offset))
                            4
                        }
                        else {
                            val rawValue =
                                    if(annotation.nonNegative) readVarInt(buffer, offset)
                                    else zigZagDecode(readVarInt(buffer, offset))
                            field.setInt(value, rawValue.toInt())
                            varIntSize(rawValue)
                        }
                    Long::class.java ->
                        if(annotation.noCompress){
                            field.setLong(value, buffer.getLong(offset))
                            8
                        }
                        else {
                            val rawValue =
                                    if(annotation.nonNegative) readVarInt(buffer, offset)
                                    else zigZagDecode(readVarInt(buffer, offset))
                            field.setLong(value, rawValue)
                            varIntSize(rawValue)
                        }
                    Float::class.java ->
                        if(annotation.noCompress){
                            field.setFloat(value, buffer.getFloat(offset))
                            4
                        }
                        else{
                            val readVal = readVarInt(buffer, offset)
                            val rawValue =
                                    if(annotation.floatEndianSwap) intToFloat(swapEndian(readVal.toInt()))
                                    else intToFloat(readVal.toInt())
                            field.setFloat(value, rawValue)
                            varIntSize(readVal)
                        }
                    Double::class.java ->
                        if(annotation.noCompress){
                            field.setDouble(value, buffer.getDouble(offset))
                            8
                        }
                        else{
                            val readVal = readVarInt(buffer, offset)
                            val rawValue =
                                    if(annotation.floatEndianSwap) longToDouble(swapEndian(readVal))
                                    else longToDouble(readVal)
                            field.setDouble(value, rawValue)
                            varIntSize(readVal)
                        }
                    else -> 0
                }
    }
}