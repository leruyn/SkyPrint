package com.dcorp.skyprint.core.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter.from
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TEST-UNIT-004b -- logic chọn interface/endpoint (REQ-004). [UsbInterface]/
 * [UsbEndpoint] có constructor thật public trên runtime Robolectric nhưng
 * bị ẩn ở SDK stub dùng lúc compile -- dựng qua [ReflectionHelpers] (kỹ
 * thuật chuẩn của Robolectric cho lớp framework có constructor ẩn), không
 * phải vấn đề pipeline đồ hoạ như ở REQ-002/CODE-006.
 */
@RunWith(RobolectricTestRunner::class)
class UsbPrinterTransportTest {

    private fun usbInterface(id: Int, interfaceClass: Int): UsbInterface = ReflectionHelpers.callConstructor(
        UsbInterface::class.java,
        from(Int::class.javaPrimitiveType, id),
        from(Int::class.javaPrimitiveType, 0), // alternateSetting
        from(String::class.java, "iface-$id"),
        from(Int::class.javaPrimitiveType, interfaceClass),
        from(Int::class.javaPrimitiveType, 0), // subClass
        from(Int::class.javaPrimitiveType, 0), // protocol
    )

    private fun usbEndpoint(address: Int, attributes: Int): UsbEndpoint = ReflectionHelpers.callConstructor(
        UsbEndpoint::class.java,
        from(Int::class.javaPrimitiveType, address),
        from(Int::class.javaPrimitiveType, attributes),
        from(Int::class.javaPrimitiveType, 64), // maxPacketSize
        from(Int::class.javaPrimitiveType, 0), // interval
    )

    private fun printerInterface(id: Int = 0) = usbInterface(id, UsbConstants.USB_CLASS_PRINTER)
    private fun otherInterface(id: Int = 0) = usbInterface(id, UsbConstants.USB_CLASS_HID)
    private fun bulkOutEndpoint() = usbEndpoint(0x01 /* bit hướng=0 -> OUT */, UsbConstants.USB_ENDPOINT_XFER_BULK)
    private fun bulkInEndpoint() = usbEndpoint(0x81 /* bit hướng=1 -> IN */, UsbConstants.USB_ENDPOINT_XFER_BULK)
    private fun interruptOutEndpoint() = usbEndpoint(0x02, UsbConstants.USB_ENDPOINT_XFER_INT)

    @Test
    fun `chon dung interface class Printer bo qua cac class khac`() {
        val picked = UsbPrinterTransport.pickPrinterInterface(listOf(otherInterface(0), printerInterface(1), otherInterface(2)))
        assertEquals(1, picked?.id)
    }

    @Test
    fun `khong co interface Printer nao tra ve null`() {
        assertNull(UsbPrinterTransport.pickPrinterInterface(listOf(otherInterface(0), otherInterface(1))))
    }

    @Test
    fun `chon dung bulk OUT endpoint bo qua bulk IN va interrupt`() {
        val picked = UsbPrinterTransport.pickBulkOutEndpoint(listOf(bulkInEndpoint(), interruptOutEndpoint(), bulkOutEndpoint()))
        assertEquals(0x01, picked?.address)
    }

    @Test
    fun `interface chi co bulk IN khong co OUT tra ve null`() {
        assertNull(UsbPrinterTransport.pickBulkOutEndpoint(listOf(bulkInEndpoint())))
    }
}
