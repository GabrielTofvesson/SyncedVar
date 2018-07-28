package net.tofvesson.serializers

import net.tofvesson.annotation.SyncFlag
import net.tofvesson.data.*
import java.lang.reflect.Field

class DiffTrackedSerializer private constructor(): Serializer(arrayOf(
        DiffTracked::class.java,
        DiffTrackedArray::class.java
)) {
    companion object {
        private val trackedField = DiffTracked::class.java.getDeclaredField("_value")
        val singleton = DiffTrackedSerializer()
    }
    override fun computeSizeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, state: WriteState, fieldType: Class<*>) {
        when (fieldType) {
            DiffTracked::class.java -> {
                val tracker = field.get(owner) as DiffTracked<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.valueType)
                state.registerHeader(1)
                if (tracker.hasChanged())
                    serializer.computeSizeExplicit(trackedField, flags, tracker, state, tracker.valueType)
            }
            DiffTrackedArray::class.java -> {
                val tracker = field.get(owner) as DiffTrackedArray<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.elementType)
                state.registerHeader(1)
                if (tracker.hasChanged()) {
                    val holder = Holder(null)
                    state.registerHeader(tracker.size)
                    for (index in tracker.changeMap.indices)
                        if (tracker.changeMap[index]) {
                            holder.value = tracker[index]
                            serializer.computeSizeExplicit(Holder.valueField, flags, holder, state, tracker.elementType)
                        }
                }
            }
            else -> throwInvalidType(fieldType)
        }
    }

    override fun serializeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, writeBuffer: WriteBuffer, fieldType: Class<*>) {
        when (fieldType) {
            DiffTracked::class.java -> {
                val tracker = field.get(owner) as DiffTracked<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.valueType)
                writeBuffer.writeHeader(tracker.hasChanged())
                if (tracker.hasChanged()) {
                    serializer.serializeExplicit(trackedField, flags, tracker, writeBuffer, tracker.valueType)
                    tracker.clearChangeState()
                }
            }
            DiffTrackedArray::class.java -> {
                val tracker = field.get(owner) as DiffTrackedArray<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.elementType)
                writeBuffer.writeHeader(tracker.hasChanged())
                if (tracker.hasChanged()) {
                    val holder = Holder(null)

                    for (index in tracker.changeMap.indices) {
                        writeBuffer.writeHeader(tracker.changeMap[index])
                        if (tracker.changeMap[index]) {
                            holder.value = tracker[index]
                            serializer.serializeExplicit(Holder.valueField, flags, holder, writeBuffer, tracker.elementType)
                        }
                    }
                    tracker.clearChangeState()
                }
            }
            else -> throwInvalidType(fieldType)
        }
    }

    override fun deserializeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, readBuffer: ReadBuffer, fieldType: Class<*>) {
        when (fieldType) {
            DiffTracked::class.java -> {
                val tracker = field.get(owner) as DiffTracked<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.valueType)
                if (readBuffer.readHeader())
                    serializer.deserializeExplicit(trackedField, flags, tracker, readBuffer, tracker.valueType)
                tracker.clearChangeState()
            }
            DiffTrackedArray::class.java -> {
                val tracker = field.get(owner) as DiffTrackedArray<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.elementType)

                if(readBuffer.readHeader()) {
                    val holder = Holder(null)

                    val array = tracker.values as Array<Any?>

                    for (index in tracker.changeMap.indices) {
                        if (readBuffer.readHeader()) {
                            serializer.deserializeExplicit(Holder.valueField, flags, holder, readBuffer, tracker.elementType)
                            array[index] = holder.value
                        }
                    }
                }
                tracker.clearChangeState()
            }
            else -> throwInvalidType(fieldType)
        }
    }
}