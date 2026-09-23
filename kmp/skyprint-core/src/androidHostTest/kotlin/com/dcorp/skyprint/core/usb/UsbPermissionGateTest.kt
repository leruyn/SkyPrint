package com.dcorp.skyprint.core.usb

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [UsbDevice] có constructor thật nhưng package-private ở SDK stub -- dựng
 * qua reflection, xem [UsbPrinterTransportTest]'s doc. Constructor không
 * tham số của Robolectric để trống `mName` (null) -- `UsbDevice.equals()`
 * (dùng bởi `ShadowUsbManager.hasPermission`'s `List.contains`) NPE nếu
 * so sánh 2 field null -- set tay field này qua reflection cho đủ dùng.
 */
private fun newUsbDevice(): UsbDevice {
    val device = ReflectionHelpers.callConstructor<UsbDevice>(UsbDevice::class.java)
    ReflectionHelpers.setField(device, "mName", "/dev/bus/usb/test")
    return device
}

/** [ShadowUsbManager.grantPermission] khai `protected` -- Kotlin không cho gọi xuyên package dù đã cast qua [shadowOf], gọi qua reflection. */
private fun grantUsbPermission(usbManager: UsbManager, device: UsbDevice) {
    ReflectionHelpers.callInstanceMethod<Unit>(
        shadowOf(usbManager),
        "grantPermission",
        ReflectionHelpers.ClassParameter.from(UsbDevice::class.java, device),
    )
}

/**
 * TEST-UNIT-004c -- [UsbPermissionGate] (REQ-004). Đây chính là chỗ đã
 * review thấy lỗi thật ở bản legacy 2 lần trong phiên này (KMP cũ đọc
 * `EXTRA_PERMISSION_GRANTED` thay vì `hasPermission`, Flutter cũ trả kết
 * quả TRƯỚC KHI người dùng bấm) -- test cả 2 kịch bản đó tường minh.
 *
 * `UsbManager.requestPermission()` không được Robolectric shadow tự động
 * bắn broadcast trả về (đã kiểm bằng javap -- không có `@Implementation`
 * cho hàm này), nên test tự mô phỏng 2 vế của giao dịch thật trên máy:
 * (1) code gọi `requestPermission` (không cần chờ nó làm gì), (2) test tự
 * đóng vai "người dùng bấm Cho phép" bằng cách gọi `grantPermission` rồi
 * gửi lại đúng broadcast mà [UsbPermissionGate] đang lắng nghe.
 */
@RunWith(RobolectricTestRunner::class)
class UsbPermissionGateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val device = newUsbDevice()
    private val action = context.packageName + ".skyprint.USB_PERMISSION"

    @Test
    fun `da co quyen san tra ve true ngay khong can broadcast`() = runTest {
        grantUsbPermission(usbManager, device)
        assertTrue(UsbPermissionGate.ensure(context, usbManager, device, timeoutMs = 1000))
    }

    @Test
    fun `nguoi dung bam Cho phep sau do moi tra ve true`() = runTest {
        val deferred = async { UsbPermissionGate.ensure(context, usbManager, device, timeoutMs = 5000) }

        // Người dùng CHƯA bấm gì -- chưa có quyền.
        assertFalse(usbManager.hasPermission(device))

        // Người dùng bấm "Cho phép": hệ thống cấp quyền RỒI MỚI gửi lại broadcast --
        // đúng thứ tự thật, khác bug cũ (trả true ngay khi vừa gọi requestPermission,
        // trước khi ai bấm gì).
        grantUsbPermission(usbManager, device)
        context.sendBroadcast(Intent(action))

        assertTrue(deferred.await())
    }

    @Test
    fun `nguoi dung tu choi tra ve false du extra co the sai`() = runTest {
        val deferred = async { UsbPermissionGate.ensure(context, usbManager, device, timeoutMs = 5000) }

        // Từ chối: KHÔNG grantPermission -- nhưng vẫn gửi broadcast với extra "granted=true"
        // giả mạo/lỗi (mô phỏng đúng bug cũ: PendingIntent FLAG_IMMUTABLE khiến hệ thống
        // không ghi được extra thật, intent nhận được có thể sai). Gate phải đọc lại
        // hasPermission() thay vì tin extra -- nên vẫn phải trả false.
        context.sendBroadcast(Intent(action).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true))

        assertFalse(deferred.await())
    }

    @Test
    fun `khong ai bam gi het het thoi gian cho tra ve false`() = runTest {
        assertFalse(UsbPermissionGate.ensure(context, usbManager, device, timeoutMs = 50))
    }

    @Test
    fun `broadcast voi action khac khong lam gate ket thuc som`() = runTest {
        val deferred = async { UsbPermissionGate.ensure(context, usbManager, device, timeoutMs = 300) }
        context.sendBroadcast(Intent("some.other.action"))
        // Gate không được kết thúc vì broadcast không liên quan -- vẫn false sau khi hết timeout.
        assertFalse(deferred.await())
    }
}
