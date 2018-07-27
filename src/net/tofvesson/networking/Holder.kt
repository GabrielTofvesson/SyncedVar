package net.tofvesson.networking

data class Holder(var value: Any?){
    companion object {
        val valueField = Holder::class.java.getDeclaredField("value")!!
    }
}