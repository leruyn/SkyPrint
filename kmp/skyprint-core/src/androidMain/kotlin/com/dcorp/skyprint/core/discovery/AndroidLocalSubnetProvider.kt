package com.dcorp.skyprint.core.discovery

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

/**
 * Lấy IP hiện tại qua [ConnectivityManager] rồi suy ra dải /24 bằng
 * [deriveSubnetHosts] (REQ-003, DESIGN-003). `null` nếu không có mạng
 * hoặc mạng hiện tại không phải IPv4 (vd chỉ có IPv6) -- [LanDiscovery]
 * coi đó là "không quét được", không phải lỗi.
 */
class AndroidLocalSubnetProvider(private val context: Context) : LocalSubnetProvider {
    override fun currentSubnetHosts(): List<String>? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null

        val ipv4 = linkProperties.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
            ?: return null

        return deriveSubnetHosts(ipv4)
    }
}
