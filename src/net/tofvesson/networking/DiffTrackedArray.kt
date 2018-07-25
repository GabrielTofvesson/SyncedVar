package net.tofvesson.networking

class DiffTrackedArray<T>(val elementType: Class<T>, val size: Int, gen: (Int) -> T) {

    val values: Array<T> = java.lang.reflect.Array.newInstance(elementType, size) as Array<T>
    val changeMap = Array(size) {false}

    init{
        for(index in 0 until size)
            values[index] = gen(index)
    }

    operator fun get(index: Int) = values[index]
    operator fun set(value: T, index: Int) {
        changeMap[index] = changeMap[index] or (values[index] != value)
        values[index] = value
    }

    fun clearChangeState(){
        for (index in changeMap.indices)
            changeMap[index] = false
    }
    fun hasChanged(): Boolean {
        for(value in changeMap)
            if(value)
                return true
        return false
    }

    override fun toString() = values.toString()
}