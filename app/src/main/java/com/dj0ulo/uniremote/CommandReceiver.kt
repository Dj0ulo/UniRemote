package com.dj0ulo.uniremote

import android.util.Log
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.net.*
import java.util.*

class CommandReceiver (private val callback: (String) -> Unit): Runnable{
    override fun run() {
        listenHttp()
    }
    private fun strFromBytes(bytes: ByteArray) : String{
        val last = bytes.indexOf(0)
        return String(bytes,0,last)
    }
    private fun listenUDP() {
        val buffer = ByteArray(32)
        var socket: DatagramSocket? = null
        while (true) {
            try {
                socket = DatagramSocket(port, InetAddress.getByName(address))
                socket.broadcast = true
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                this.callback(strFromBytes(packet.data))
            } catch (e: Exception) {
                Log.e(MainActivity().TAG, "[UDP] Exception : $e")
                e.printStackTrace()
            } finally {
                socket?.close()
            }
        }
    }
    private fun listenHttp() {
        embeddedServer(Netty, port) {
            install(ContentNegotiation) {
                gson()
            }
            routing {
                put{
                    val instruction = call.parameters["msg"]?:""
                    call.respond(mapOf("message" to "ok"))
                    callback(instruction)
                }
            }
        }.start(wait = true)
    }
    companion object {
        private val address = getLocalIpAddress() //"255.255.255.255"
        private val port = 6510;
        fun getLocalIpAddress(): String {
            try {
                val en = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val intf: NetworkInterface = en.nextElement()
                    val enumIpAddr = intf.inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress: InetAddress = enumIpAddr.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            return inetAddress.hostAddress
                        }
                    }
                }
            } catch (ex: SocketException) {
                ex.printStackTrace()
            }
            return ""
        }
        fun url() : String{
            return "http://$address:$port"
        }
    }

}