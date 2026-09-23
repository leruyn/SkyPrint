package com.dcorp.skyprint.core.discovery

/**
 * Suy ra danh sách 254 host trong dải /24 chứa [ipv4] (REQ-003, DESIGN-003)
 * -- tách khỏi cách LẤY [ipv4] thật (khác nhau theo nền tảng, xem
 * `AndroidLocalSubnetProvider`) để phần toán học subnet test được không
 * cần Android/iOS.
 */
internal fun deriveSubnetHosts(ipv4: String): List<String>? {
    val octets = ipv4.trim().split(".")
    if (octets.size != 4) return null
    val prefix = octets.take(3)
    if (prefix.any { it.toIntOrNull() !in 0..255 }) return null
    val prefixStr = prefix.joinToString(".")
    return (1..254).map { "$prefixStr.$it" }
}
