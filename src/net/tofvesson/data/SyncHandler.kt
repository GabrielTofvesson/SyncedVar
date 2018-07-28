package net.tofvesson.data

import net.tofvesson.annotation.NoUpwardCascade
import net.tofvesson.annotation.SyncFlag
import net.tofvesson.annotation.SyncedVar
import net.tofvesson.exception.UnsupportedTypeException
import net.tofvesson.reflect.access
import net.tofvesson.serializers.DiffTrackedSerializer
import net.tofvesson.serializers.MathSerializer
import net.tofvesson.serializers.PrimitiveArraySerializer
import net.tofvesson.serializers.PrimitiveSerializer
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.security.NoSuchAlgorithmException
import java.security.MessageDigest

@Suppress("MemberVisibilityCanBePrivate", "unused")
/**
 * @param permissiveMismatchCheck This should essentially never be set to true aside from some *very* odd edge cases
 */
class SyncHandler(private val permissiveMismatchCheck: Boolean = false) {
    private val toSync: ArrayList<Any> = ArrayList()
    companion object {
        private val serializers: ArrayList<Serializer> = ArrayList()

        init {
            // Standard serializers
            serializers.add(PrimitiveSerializer.singleton)
            serializers.add(PrimitiveArraySerializer.singleton)
            serializers.add(DiffTrackedSerializer.singleton)
            serializers.add(MathSerializer.singleton)
        }

        fun registerSerializer(serializer: Serializer) {
            if(!serializers.contains(serializer))
                serializers.add(serializer)
        }

        fun unregisterSerializer(serializer: Serializer) {
            serializers.remove(serializer)
        }

        fun clearSerializers() = serializers.clear()
        fun getRegisteredSerializers() = serializers.toArray()
        fun getCompatibleSerializer(type: Class<*>): Serializer {
            for(serializer in serializers)
                if(serializer.canSerialize(type))
                    return serializer
            throw UnsupportedTypeException("Cannot find a compatible serializer for $type")
        }


        private fun collectSyncable(fieldType: Class<*>, collectStatic: Boolean): List<Field>{
            var type = fieldType
            val collect = ArrayList<Field>()

            while(type!=Object::class.java && !type.isPrimitive && type.getAnnotation(NoUpwardCascade::class.java)==null){
                for(field in type.declaredFields)
                    if(field.getAnnotation(SyncedVar::class.java)!=null && ((collectStatic && field.modifiers and 8 != 0) || (!collectStatic && field.modifiers and 8 == 0)))
                        collect.add(field)
                type = type.superclass
            }

            return collect
        }
    }

    fun registerSyncObject(value: Any){
        if(!toSync.contains(value)) toSync.add(value)
    }

    fun unregisterSyncObject(value: Any){
        toSync.remove(value)
    }

    fun serialize(): ByteBuffer {
        val writeState = WriteState(0, 0, 0)
        for(entry in toSync)
            if(entry is Class<*>) computeClassSize(entry, writeState)
            else computeObjectSize(entry, writeState)
        val writeBuffer = WriteBuffer(writeState)
        for(entry in toSync)
            if(entry is Class<*>) readClass(entry, writeBuffer)
            else readObject(entry, writeBuffer)
        return writeBuffer.buffer
    }

    fun deserialize(syncData: ByteArray) = deserialize(syncData, 0)
    fun deserialize(syncData: ByteArray, bitOffset: Int){
        val writeState = WriteState(0, 0, 0)
        for(entry in toSync)
            if(entry is Class<*>) computeClassSize(entry, writeState)
            else computeObjectSize(entry, writeState)
        val readBuffer = ReadBuffer(writeState, ByteBuffer.wrap(syncData), bitOffset)
        for(entry in toSync)
            if(entry is Class<*>) writeClass(entry, readBuffer)
            else writeObject(entry, readBuffer)
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


    private fun computeObjectSize(value: Any, writeState: WriteState) = computeTypeSize(value.javaClass, value, writeState)
    private fun computeClassSize(value: Class<*>, writeState: WriteState) = computeTypeSize(value, null, writeState)
    private fun computeTypeSize(type: Class<*>, value: Any?, writeState: WriteState) {
        for(field in collectSyncable(type, value == null))
            getCompatibleSerializer(field.access().type)
                    .computeSize(field, SyncFlag.parse(field.getAnnotation(SyncedVar::class.java).value), value, writeState)
    }

    private fun readObject(value: Any, writeBuffer: WriteBuffer) = readType(value.javaClass, value, writeBuffer)
    private fun readClass(value: Class<*>, writeBuffer: WriteBuffer) = readType(value, null, writeBuffer)
    private fun readType(type: Class<*>, value: Any?, writeBuffer: WriteBuffer) {
        for(field in collectSyncable(type, value == null))
            getCompatibleSerializer(field.type)
                    .serialize(field, SyncFlag.parse(field.getAnnotation(SyncedVar::class.java).value), value, writeBuffer)
    }

    private fun writeObject(value: Any, readBuffer: ReadBuffer) = writeType(value.javaClass, value, readBuffer)
    private fun writeClass(value: Class<*>, readBuffer: ReadBuffer) = writeType(value, null, readBuffer)
    private fun writeType(type: Class<*>, value: Any?, readBuffer: ReadBuffer) {
        for(field in collectSyncable(type, value == null))
            getCompatibleSerializer(field.type)
                    .deserialize(field, SyncFlag.parse(field.getAnnotation(SyncedVar::class.java).value), value, readBuffer)
    }
}