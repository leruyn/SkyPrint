package com.dcorp.skyprint.core.bt

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dcorp.skyprint.core.error.PrinterErrorCode
import com.dcorp.skyprint.core.error.PrinterException
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.TransportKind
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBluetoothDevice
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** UUID Serial Port Profile chuẩn -- trùng hằng số nội bộ của [BtClassicPrinterTransport] (không lộ ra ngoài để test gọi tới). */
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

/**
 * TEST-UNIT-006 -- [BtClassicPrinterTransport] (REQ-006). Robolectric có
 * shadow đầy đủ cho BluetoothAdapter/BluetoothDevice/BluetoothSocket
 * (dữ liệu thuần, không qua Skia native) -- test được thật đầu-cuối
 * open()/write()/close() không cần thiết bị Bluetooth thật, khác gap của
 * REQ-007's BLE (Kable không có fake).
 */
// sdk=33: mặc định (không khai) Robolectric có thể chọn SDK dưới 31, khi đó
// BLUETOOTH_CONNECT không phải quyền runtime nên check quyền trong code sản
// phẩm luôn qua sớm -- không exercise được nhánh PERMISSION_REQUIRED.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BtClassicPrinterTransportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    @Before
    fun setUp() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        shadowOf(adapter).setEnabled(true)
    }

    @After
    fun resetShadow() {
        ShadowBluetoothDevice.reset()
    }

    private fun bondedDevice(address: String = "AA:BB:CC:DD:EE:FF", name: String = "ICOD Printer"): BluetoothDevice {
        val device = ShadowBluetoothDevice.newInstance(address)
        shadowOf(device).setName(name)
        adapter.let { shadowOf(it).setBondedDevices(mutableSetOf(device)) }
        return device
    }

    private fun printer(address: String, name: String = "Test") =
        PrinterInfo(id = "bt:$address", name = name, kind = TransportKind.BT_CLASSIC, address = address)

    @Test
    fun `khong co quyen BLUETOOTH_CONNECT tra PERMISSION_REQUIRED`() = runTest {
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val transport = BtClassicPrinterTransport(context)

        val ex = assertFailsWith<PrinterException> { transport.open(printer("AA:BB:CC:DD:EE:FF")) }
        assertEquals(PrinterErrorCode.PERMISSION_REQUIRED, ex.code)
    }

    @Test
    fun `khong tim thay thiet bi da ghep noi tra NOT_FOUND`() = runTest {
        bondedDevice(address = "11:11:11:11:11:11")
        val transport = BtClassicPrinterTransport(context)

        val ex = assertFailsWith<PrinterException> { transport.open(printer("99:99:99:99:99:99")) }
        assertEquals(PrinterErrorCode.NOT_FOUND, ex.code)
    }

    @Test
    fun `ket noi va ghi thanh cong toi thiet bi da ghep noi`() = runTest {
        val device = bondedDevice()
        val transport = BtClassicPrinterTransport(context)

        val connection = transport.open(printer(device.address))
        connection.write(byteArrayOf(1, 2, 3, 4, 5))
        connection.close()

        // ShadowBluetoothDevice dùng CHUNG 1 BluetoothSocket giả cho mọi lần gọi
        // createRfcommSocketToServiceRecord trong tiến trình test -- gọi lại đây để
        // lấy đúng instance đó rồi đọc outputStreamSink, xác nhận byte THẬT SỰ đã
        // được ghi (không chỉ không throw exception).
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        val writtenBytes = shadowOf(socket).outputStreamSink.readNBytes(5)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), writtenBytes)
    }

    @Test
    fun `listCandidates tra dung danh sach thiet bi da ghep noi`() = runTest {
        val a = bondedDevice(address = "11:11:11:11:11:11", name = "B Printer")
        val b = ShadowBluetoothDevice.newInstance("22:22:22:22:22:22")
        shadowOf(b).setName("A Printer")
        shadowOf(adapter).setBondedDevices(mutableSetOf(a, b))

        val candidates = BtClassicPrinterTransport.listCandidates(context)

        assertEquals(2, candidates.size)
        assertEquals(listOf("A Printer", "B Printer"), candidates.map { it.name }, "phải sắp theo tên")
        assertTrue(candidates.all { it.kind == TransportKind.BT_CLASSIC })
    }

    @Test
    fun `listCandidates rong khi thieu quyen`() = runTest {
        bondedDevice()
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)

        assertEquals(emptyList(), BtClassicPrinterTransport.listCandidates(context))
    }
}
