package com.example.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun getDeviceIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty() && !host.startsWith("127.")) {
                            ips.add(host)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (ips.isEmpty()) {
            ips.add("127.0.0.1")
        }
        return ips
    }
}
