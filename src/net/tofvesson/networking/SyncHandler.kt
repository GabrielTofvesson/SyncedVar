package net.tofvesson.networking

import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.security.NoSuchAlgorithmException
import java.security.MessageDigest


/**
 * @param permissiveMismatchCheck This should essentially never be set to true aside from some *very* odd edge cases
 */
class SyncHandler(defaultSerializers: Boolean = true, private val permissiveMismatchCheck: Boolean = false) {
    private val toSync: ArrayList<Any> = ArrayList()
    private val serializers: ArrayList<Serializer> = ArrayList()

    init {
        if(defaultSerializers) {
            serializers.add(PrimitiveSerializer.singleton)
            serializers.add(PrimitiveArraySerializer.singleton)
        }
    }

    fun registerSyncObject(value: Any){
        if(!toSync.contains(value)) toSync.add(value)
    }

    fun unregisterSyncObject(value: Any){
        toSync.remove(value)
    }

    fun withSerializer(serializer: Serializer): SyncHandler {
        if(!serializers.contains(serializer)) serializers.add(serializer)
        return this
    }

    fun serialize(): ByteArray{
        var headerSize = 0
        var totalSize = 0
        for(entry in toSync){
            val result = if(entry is Class<*>) computeClassSize(entry) else computeObjectSize(entry)
            totalSize += result.first
            headerSize += result.second
        }

        var headerIndex = 0
        var dataIndex = (headerSize shr 3) + (if(headerSize and 7 != 0) 1 else 0)

        totalSize += dataIndex

        val buffer = ByteArray(totalSize)
        for(entry in toSync){
            val result =
                    if(entry is Class<*>) readClass(entry, buffer, dataIndex, headerIndex)
                    else readObject(entry, buffer, dataIndex, headerIndex)
            dataIndex = result.first
            headerIndex = result.second
        }
        return buffer
    }

    fun deserialize(syncData: ByteArray){
        var headerSize = 0
        for(entry in toSync)
            headerSize += (if(entry is Class<*>) computeClassSize(entry) else computeObjectSize(entry)).second
        var headerIndex = 0
        var dataIndex = (headerSize shr 3) + (if(headerSize and 7 != 0) 1 else 0)
        for(entry in toSync){
            val result =
                    if(entry is Class<*>) writeClass(entry, syncData, dataIndex, headerIndex)
                    else writeObject(entry, syncData, dataIndex, headerIndex)
            dataIndex = result.first
            headerIndex = result.second
        }
    }

    fun generateMismatchCheck(): ByteArray {
        val builder = StringBuilder()
        for(entry in toSync)
            for(field in (entry as? Class<*> ?: entry::class.java).declaredFields){
                if((entry is Class<*> && field.modifiers and 8 == 0) || (entry !is Class<*> && field.modifiers and 8 != 0)) continue
                val annotation = field.getAnnotation(SyncedVar::class.java)
                if(annotation!=null) {
                    builder.append('{').append(if (permissiveMismatchCheck) field.type.name else field.toGenericString())
                    for(flag in annotation.value) builder.append(flag)
                    builder.append('}')
                }
            }

        return try {
            MessageDigest.getInstance("SHA-1").digest(builder.toString().toByteArray())
        } catch (e: NoSuchAlgorithmException) {
            builder.toString().toByteArray()
        }
    }

    fun doMismatchCheck(check: ByteArray): Boolean {
        val mismatchCheck = generateMismatchCheck()
        if(mismatchCheck.size != check.size) return false
        for(index in mismatchCheck.indices)
            if(mismatchCheck[index]!=check[index])
                return false
        return true
    }


    private fun computeObjectSize(value: Any) = computeTypeSize(value.javaClass, value)
    private fun computeClassSize(value: Class<*>) = computeTypeSize(value, null)
    private fun computeTypeSize(type: Class<*>, value: Any?): Pair<Int, Int> {
        var byteSize = 0
        var bitSize = 0
        for(field in type.declaredFields){
            if((value == null && field.modifiers and 8 == 0) || (value != null && field.modifiers and 8 != 0)) continue
            val annotation = field.getAnnotation(SyncedVar::class.java)
            field.trySetAccessible()
            if(annotation!=null){
                val result = getCompatibleSerializer(field.type).computeSize(field, SyncFlag.parse(annotation.value), value)
                byteSize += result.first
                bitSize += result.second
            }
        }
        return Pair(byteSize, bitSize)
    }
    private fun getCompatibleSerializer(type: Class<*>): Serializer {
        for(serializer in serializers)
            if(serializer.canSerialize(type))
                return serializer
        throw UnsupportedTypeException("Cannot find a compatible serializer for $type")
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
                val result = getCompatibleSerializer(field.type).serialize(field, SyncFlag.parse(annotation.value), value, byteBuffer, localOffset, localBitOffset)
                localOffset = result.first
                localBitOffset = result.second
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
                val result = getCompatibleSerializer(field.type).deserialize(field, SyncFlag.parse(annotation.value), value, byteBuffer, localOffset, localBitOffset)
                localOffset = result.first
                localBitOffset = result.second
            }
        }
        return Pair(localOffset, localBitOffset)
    }
}