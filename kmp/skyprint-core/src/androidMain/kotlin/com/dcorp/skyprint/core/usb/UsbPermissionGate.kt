package com.dcorp.skyprint.core.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Xin quyền truy cập thiết bị USB (REQ-004, DESIGN-004) -- tách khỏi
 * [UsbPrinterTransport] để có thể thay bằng bản giả trong test không đụng
 * tới `PendingIntent`/`BroadcastReceiver` thật nếu cần.
 *
 * Theo đúng hướng dẫn USB host của Android: [PendingIntent] với action tự
 * đặt (không dùng hằng số Android sẵn có, tránh app khác gửi trùng), nhận
 * qua [BroadcastReceiver] đăng ký `RECEIVER_NOT_EXPORTED` khi có thể (API
 * 33+, bắt buộc từ Android 13) -- gọi thẳng `Context.registerReceiver`
 * theo `Build.VERSION.SDK_INT` thay vì qua `ContextCompat` (compat shim
 * của AndroidX cho hành vi "not exported" pre-33 đòi khai permission
 * riêng, không cần thiết vì chỉ 1 nhánh SDK_INT là đủ). `FLAG_MUTABLE` +
 * `Intent.setPackage` (không phải hằng số hệ thống)
 * để hệ thống có thể ghi `EXTRA_PERMISSION_GRANTED` vào intent khi resend.
 *
 * Sau khi nhận broadcast, quyết định dựa trên [UsbManager.hasPermission]
 * đọc LẠI trực tiếp -- KHÔNG tin `EXTRA_PERMISSION_GRANTED` trong intent.
 * Lý do: nếu `PendingIntent` được tạo `FLAG_IMMUTABLE` ở đâu đó trong vòng
 * đời app (nhầm lẫn dễ xảy ra khi copy code mẫu cũ), hệ thống có thể không
 * ghi được extra vào intent khi gửi lại, khiến app luôn đọc ra `false` dù
 * người dùng đã bấm "Cho phép" thật -- đọc lại `hasPermission` tránh được
 * lớp lỗi này hoàn toàn, không phụ thuộc flag nào.
 */
internal object UsbPermissionGate {
    private const val ACTION_SUFFIX = ".skyprint.USB_PERMISSION"

    suspend fun ensure(context: Context, usbManager: UsbManager, device: UsbDevice, timeoutMs: Long): Boolean {
        if (usbManager.hasPermission(device)) return true
        return withTimeoutOrNull(timeoutMs) { awaitPermission(context, usbManager, device) } ?: false
    }

    private suspend fun awaitPermission(context: Context, usbManager: UsbManager, device: UsbDevice): Boolean =
        suspendCancellableCoroutine { continuation ->
            val action = context.packageName + ACTION_SUFFIX

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receivedContext: Context, intent: Intent) {
                    if (intent.action != action) return
                    unregisterQuietly(context, this)
                    if (continuation.isActive) continuation.resumeWith(Result.success(usbManager.hasPermission(device)))
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, IntentFilter(action))
            }
            continuation.invokeOnCancellation { unregisterQuietly(context, receiver) }

            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE,
            )
            usbManager.requestPermission(device, permissionIntent)
        }

    private fun unregisterQuietly(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: IllegalArgumentException) {
            // Đã unregister rồi (vd coroutine bị huỷ trước) -- an toàn bỏ qua.
        }
    }
}
