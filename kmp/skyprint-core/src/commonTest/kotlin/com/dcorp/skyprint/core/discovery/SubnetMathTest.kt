package com.dcorp.skyprint.core.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubnetMathTest {
    @Test
    fun `sinh dung 254 host tu 1 den 254 cung prefix`() {
        val hosts = deriveSubnetHosts("192.168.1.42")
        assertEquals(254, hosts?.size)
        assertEquals("192.168.1.1", hosts?.first())
        assertEquals("192.168.1.254", hosts?.last())
        // IP của chính thiết bị cũng nằm trong dải quét -- không loại trừ đặc biệt (probe IP mình cũng vô hại, chỉ tốn 1 lượt).
        assertTrue(hosts!!.contains("192.168.1.42"))
    }

    @Test
    fun `dia chi khong hop le tra ve null`() {
        assertNull(deriveSubnetHosts("khong-phai-ip"))
        assertNull(deriveSubnetHosts("1.2.3"))
        assertNull(deriveSubnetHosts("1.2.3.4.5"))
        assertNull(deriveSubnetHosts("1.2.300.4"))
    }
}
