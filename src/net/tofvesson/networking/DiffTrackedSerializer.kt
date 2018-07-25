package net.tofvesson.networking

import java.lang.reflect.Field
import java.nio.ByteBuffer

class DiffTrackedSerializer private constructor(): Serializer(arrayOf(
        DiffTracked::class.java,
        DiffTrackedArray::class.java
)) {
    companion object {
        private val trackedField = DiffTracked::class.java.getDeclaredField("_value")
        private val holderValue = Holder::class.java.getDeclaredField("value")
        val singleton = DiffTrackedSerializer()
    }
    override fun computeSizeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, fieldType: Class<*>): Pair<Int, Int> =
            when(fieldType) {
                DiffTracked::class.java -> {
                    val tracker = field.get(owner) as DiffTracked<*>
                    val serializer = SyncHandler.getCompatibleSerializer(tracker.valueType)
                    if(tracker.hasChanged()){
                        val result = serializer.computeSizeExplicit(trackedField, flags, tracker, tracker.valueType)
                        Pair(result.first, result.second+1)
                    }else Pair(0, 1)
                }
                DiffTrackedArray::class.java -> {
                    val tracker = field.get(owner) as DiffTrackedArray<*>
                    val serializer = SyncHandler.getCompatibleSerializer(tracker.elementType)
                    if(tracker.hasChanged()){
                        var bits = 0
                        var bytes = 0
                        val holder = Holder(null)

                        for(index in tracker.changeMap.indices)
                            if(tracker.changeMap[index]){
                                holder.value = tracker[index]
                                val result = serializer.computeSizeExplicit(holderValue, flags, holder, tracker.elementType)
                                bytes += result.first
                                bits += result.second
                            }
                        Pair(bytes, bits+tracker.size)
                    }else Pair(0, tracker.size)
                }
                else -> Pair(0, 0)
            }

    override fun serializeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, byteBuffer: ByteBuffer, offset: Int, bitFieldOffset: Int, fieldType: Class<*>): Pair<Int, Int> =
            when(fieldType) {
                DiffTracked::class.java -> {
                    val tracker = field.get(owner) as DiffTracked<*>
                    val serializer = SyncHandler.getCompatibleSerializer(tracker.valueType)
                    writeBit(tracker.hasChanged(), byteBuffer, bitFieldOffset)
                    if(tracker.hasChanged()){
                        val result = serializer.serializeExplicit(trackedField, flags, tracker, byteBuffer, offset, bitFieldOffset+1, tracker.valueType)
                        tracker.clearChangeState()
                        Pair(result.first, result.second)
                    }else{
                        tracker.clearChangeState()
                        Pair(offset, bitFieldOffset+1)
                    }
                }
                DiffTrackedArray::class.java -> {
                    val tracker = field.get(owner) as DiffTrackedArray<*>
                    val serializer = SyncHandler.getCompatibleSerializer(tracker.elementType)
                    if(tracker.hasChanged()){
                        var bits = bitFieldOffset
                        var bytes = offset
                        val holder = Holder(null)

                        for(index in tracker.changeMap.indices) {
                            writeBit(tracker.changeMap[index], byteBuffer, bits++)
                            if (tracker.changeMap[index]) {
                                holder.value = tracker[index]
                                val result = serializer.serializeExplicit(holderValue, flags, holder, byteBuffer, bytes, bits, tracker.elementType)
                                bytes = result.first
                                bits = result.second
                            }
                        }
                        tracker.clearChangeState()
                        Pair(bytes, bits)
                    }else{
                        tracker.clearChangeState()
                        Pair(offset, bitFieldOffset+tracker.size)
                    }
                }
                else -> Pair(0, 0)
            }

    override fun deserializeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, byteBuffer: ByteBuffer, offset: Int, bitFieldOffset: Int, fieldType: Class<*>): Pair<Int, Int> =
            when(fieldType) {
                DiffTracked::class.java -> {
                    val tracker = field.get(owner) as DiffTracked<*>
                    val serializer = SyncHandler.getCompatibleSerializer(tracker.valueType)
                    var bytes = offset
                    var bits = bitFieldOffset
                    if(readBit(byteBuffer, bits++)){
                        val result = serializer.deserializeExplicit(trackedField, flags, tracker, byteBuffer, bytes, bits, tracker.valueType)
                        bytes = result.first
                        bits = result.second
                    }
                    tracker.clearChangeState()
                    Pair(bytes, bits)
                }
                DiffTrackedArray::class.java -> {
                    val tracker = field.get(owner) as DiffTrackedArray<*>
                    val serializer = SyncHandler.getCompatibleSerializer(tracker.elementType)

                    var bits = bitFieldOffset
                    var bytes = offset
                    val holder = Holder(null)

                    val array = tracker.values as Array<Any?>

                    for(index in tracker.changeMap.indices){
                        if(readBit(byteBuffer, bits++)){
                            holder.value = tracker[index]
                            val result = serializer.deserializeExplicit(holderValue, flags, holder, byteBuffer, bytes, bits, tracker.elementType)
                            bytes = result.first
                            bits = result.second
                            array[index] = holder.value
                        }
                    }
                    tracker.clearChangeState()
                    Pair(bytes, bits)
                }
                else -> Pair(0, 0)
            }

    private data class Holder(var value: Any?)
}