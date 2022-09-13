package com.dj0ulo.uniremote

import android.util.Log
import java.net.*
import java.util.*

class UDP (private val callback: (String) -> Unit): Runnable{
    private val address = getLocalIpAddress() //"255.255.255.255"
    private val port = 6455;
    override fun run() {
        while (true) {
            Log.i(MainActivity().TAG, "Listen")
            listen()
        }
    }
    private fun strFromBytes(bytes: ByteArray) : String{
        val last = bytes.indexOf(0)
        return String(bytes,0,last)
    }
    private fun listen() {
        val buffer = ByteArray(32)
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(this.port, InetAddress.getByName(this.address))
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
    companion object {
        fun getLocalIpAddress(): String {
            try {
                val en: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val intf: NetworkInterface = en.nextElement()
                    val enumIpAddr: Enumeration<InetAddress> = intf.inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress: InetAddress = enumIpAddr.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address)
                            return inetAddress.hostAddress
                    }
                }
            } catch (ex: SocketException) {
                ex.printStackTrace()
            }
            return ""
        }
    }
}