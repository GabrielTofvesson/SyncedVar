package net.tofvesson.networking

import net.tofvesson.reflect.*

/**
 * An annotation denoting that a field should be automatically serialized to bytes.
 * @param noCompress specifies whether or not the SyncedVar value should be losslessly compressed during serialization
 * @param nonNegative An advanced compression flag that may decrease serialization size at the cost of never having
 * negative values. NOTE: If the field is not a <i>short</i>, <i>int</i> or <i>long</i> or <b>noCompress</b> is set to
 * <b>true</b>, this parameter is ignored. This will cause errors if used in conjunction with a negative integer value.
 * @param floatEndianSwap Whether or not floating point values should have their endianness swapped before compression.
 * Swapping endianness may improve compression rates. This parameter is ignored if <b>noCompress</b> is set to <b>true</b>.
 */
@Target(allowedTargets = [(AnnotationTarget.FIELD)])
annotation class SyncedVar(vararg val value: String = [])

enum class SyncFlag {
    NoCompress,
    NonNegative,
    FloatEndianSwap;

    companion object {
        fun getByName(name: String): SyncFlag? {
            for(flag in SyncFlag.values())
                if(flag.name == name)
                    return flag
            return null
        }

        fun parse(flagSet: Array<out String>): Array<SyncFlag> = Array(flagSet.size) { getByName(flagSet[it])!! }

        fun createFlag(name: String): SyncFlag {
            // Do duplication check
            if(getByName(name)!=null) throw IllegalArgumentException("Flag \"$name\" is already registered!")

            // Get unsafe
            val unsafe = getUnsafe()

            // Create new enum
            val newFlag: SyncFlag = unsafe.allocateInstance(SyncFlag::class.java) as SyncFlag

            // Set valid ordinal
            val ordinalField = Enum::class.java.getDeclaredField("ordinal")
            ordinalField.forceAccessible = true
            ordinalField.setInt(newFlag, getUnallocatedOrdinal())

            // Set valid name
            val nameField = Enum::class.java.getDeclaredField("name")
            nameField.forceAccessible = true
            nameField.set(newFlag, name)

            // Add the new flag to the array of values
            val oldFlags = values()
            val flagArray = Array(oldFlags.size+1){
                if(it < oldFlags.size) oldFlags[it]
                else newFlag
            }
            val flags = SyncFlag::class.java.getDeclaredField("\$VALUES")
            flags.setStaticFinalValue(flagArray)

            return newFlag
        }

        private fun getUnallocatedOrdinal(): Int {
            var ordinal = 0
            val flags = SyncFlag.values().toMutableList()
            val remove = ArrayList<SyncFlag>()
            while(flags.count()!=0) {
                for (flag in flags)
                    if (flag.ordinal == ordinal) {
                        remove.add(flag)
                        ++ordinal
                        if (ordinal == 0) throw IndexOutOfBoundsException("Full 32-bit range or enum ordinals occupied!")
                    }
                flags.removeAll(remove)
                remove.clear()
            }
            return ordinal
        }
    }
}