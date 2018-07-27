package net.tofvesson.math

fun Boolean.toNumber() = if(this) 1 else 0
fun Number.toBoolean() = this!=0

fun Int.collapseLowerByte(): Int =
        ((this ushr 7) or
                (this ushr 6) or
                (this ushr 5) or
                (this ushr 4) or
                (this ushr 3) or
                (this ushr 2) or
                (this ushr 1)) and 1