package net.tofvesson.networking

import net.tofvesson.reflect.access
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class ReliableUDP(address: InetAddress, port: Short, private val onAccept: (ByteArray, Int, Int) -> Unit, private val automaticAccept: Boolean, acceptTimeout: Long = 10L) {

    private enum class PacketType(val bits: Int = (javaClass.getDeclaredField("\$VALUES").access().get(null) as Array<*>).indexOf(this)) {
        DATA, ACK, FIN;
        companion object {
            val fieldSize = (Math.log((javaClass.getDeclaredField("\$VALUES").access().get(null) as Array<*>).size.toDouble()) / Math.log(2.0)).toInt() + 1
        }
    }

    private var socket = DatagramSocket()
    private val sync: Thread? = if(automaticAccept) Thread { acceptLoop() } else null
    private val stop = AtomicBoolean(false)
    private var finInitiated = false
    private val packet = DatagramPacket(ByteArray(socket.receiveBufferSize), socket.receiveBufferSize)

    init {
        socket.connect(address, port.toInt())
        socket.soTimeout = 0
        sync?.start()
    }

    private fun acceptLoop(){
        while(synchronized(stop){!stop.get()}){
            accept()
            if(packet.length==0) continue // Drop empty packets
            val packetType = PacketType.values()[packet.data[0].toInt()]
            when(packetType){
                PacketType.DATA -> {

                }

                PacketType.ACK -> {

                }

                PacketType.FIN -> {

                }
            }
        }
    }

    fun accept(){
        if(automaticAccept && Thread.currentThread() != sync)
            throw IllegalThreadStateException("Current thread isn't setup to accept datagram packets")
        socket.receive(packet)
    }

    fun send(){
        if(finInitiated) throw IllegalStateException("Cannot send message after connection is closed!")
    }

    fun _send(){

    }
}