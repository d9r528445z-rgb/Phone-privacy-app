package com.familyguard.child.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtil {
    /** Best-effort local IPv4 address (e.g. 192.168.x.x) for showing the parent the URL. */
    fun localIpv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (t: Throwable) {
            null
        }
    }
}
