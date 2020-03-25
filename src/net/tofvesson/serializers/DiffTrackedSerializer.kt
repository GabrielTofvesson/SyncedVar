package net.tofvesson.serializers

import net.tofvesson.annotation.SyncFlag
import net.tofvesson.data.*
import net.tofvesson.reflect.access
import java.lang.reflect.Field

class DiffTrackedSerializer private constructor(): Serializer(arrayOf(
        DiffTracked::class.java,
        DiffTrackedArray::class.java
)) {
    companion object {
        private val trackedField = DiffTracked::class.java.getDeclaredField("_value")
        val singleton = DiffTrackedSerializer()

        /**
         * Checks if a given object/class needs to ve serialized by checking if any DiffTracked/DiffTrackedArray
         * instances in the given object/class are dirty
         */
        fun objectNeedsSerialization(obj: Any?) =
                obj == null ||
                        ((obj as? Class<*> ?: obj.javaClass).fields.firstOrNull {
                            if (it.type is DiffTracked<*> || it.type is DiffTrackedArray<*>){
                                it.access()
                                val diff = it.get(if(obj is Class<*>) null else obj)
                                return@firstOrNull diff.javaClass.getDeclaredMethod("hasChanged").invoke(diff) as Boolean
                            }

                            return@firstOrNull false
                        } != null)
    }
    override fun computeSizeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, state: WriteState, fieldType: Class<*>) {
        when (fieldType) {
            DiffTracked::class.java -> {
                val tracker = field.get(owner) as DiffTracked<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.valueType) ?: return
                state.registerBits(1)
                if (tracker.hasChanged())
                    serializer.computeSizeExplicit(trackedField, flags, tracker, state, tracker.valueType)
            }
            DiffTrackedArray::class.java -> {
                val tracker = field.get(owner) as DiffTrackedArray<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.elementType) ?: return
                state.registerBits(1)
                if (tracker.hasChanged()) {
                    val holder = Holder(null)
                    state.registerBits(tracker.size)
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

    override fun serializeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, writeBuffer: WBuffer, fieldType: Class<*>) {
        when (fieldType) {
            DiffTracked::class.java -> {
                val tracker = field.get(owner) as DiffTracked<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.valueType) ?: return
                writeBuffer.writeBit(tracker.hasChanged())
                if (tracker.hasChanged()) {
                    serializer.serializeExplicit(trackedField, flags, tracker, writeBuffer, tracker.valueType)
                    tracker.clearChangeState()
                }
            }
            DiffTrackedArray::class.java -> {
                val tracker = field.get(owner) as DiffTrackedArray<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.elementType) ?: return
                writeBuffer.writeBit(tracker.hasChanged())
                if (tracker.hasChanged()) {
                    val holder = Holder(null)

                    for (index in tracker.changeMap.indices) {
                        writeBuffer.writeBit(tracker.changeMap[index])
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

    override fun deserializeExplicit(field: Field, flags: Array<out SyncFlag>, owner: Any?, readBuffer: RBuffer, fieldType: Class<*>) {
        when (fieldType) {
            DiffTracked::class.java -> {
                val tracker = field.get(owner) as DiffTracked<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.valueType) ?: return
                if (readBuffer.readBit())
                    serializer.deserializeExplicit(trackedField, flags, tracker, readBuffer, tracker.valueType)
                tracker.clearChangeState()
            }
            DiffTrackedArray::class.java -> {
                val tracker = field.get(owner) as DiffTrackedArray<*>
                val serializer = SyncHandler.getCompatibleSerializer(tracker.elementType) ?: return

                if(readBuffer.readBit()) {
                    val holder = Holder(null)

                    val array = tracker.values as Array<Any?>

                    for (index in tracker.changeMap.indices) {
                        if (readBuffer.readBit()) {
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

    override fun canSerialize(obj: Any?, flags: Array<out SyncFlag>, type: Class<*>): Boolean {
        if (obj == null)
            return false

        if (DiffTracked::class.java.isAssignableFrom(type)){
            obj as DiffTracked<*>

            val handler = SyncHandler.getCompatibleSerializer(obj.valueType) ?: return false

            return handler.canSerialize(obj.value, flags, obj.valueType)
        }else if (DiffTrackedArray::class.java.isAssignableFrom(type)){
            obj as DiffTrackedArray<*>

            val handler = SyncHandler.getCompatibleSerializer(obj.elementType) ?: return false

            return handler.canSerialize(obj.values, flags, obj.elementType)
        }

        return false
    }
}