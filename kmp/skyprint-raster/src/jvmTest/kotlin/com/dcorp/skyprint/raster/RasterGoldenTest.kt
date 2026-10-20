package com.dcorp.skyprint.raster

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Hồi quy byte-by-byte so với `sky-printer` của SkytabOffline (GoldenReceiptTest.golden_raster_anh_xam_gia, file
 * raster-synthetic.txt): cùng ảnh xám giả, không phụ thuộc font. Nếu test này đỏ thì Floyd–Steinberg hoặc lệnh GS v 0
 * đã đổi so với bản đã test trên máy in thật -- KHÔNG sửa hằng số cho xanh trước khi hiểu vì sao.
 */
class RasterGoldenTest {
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun raster_anh_xam_gia_khop_golden_cua_sky_printer() {
        val w = 100; val h = 40
        val gray = GrayBitmap(w, h, ByteArray(w * h) { i -> (((i % w) * 7 + (i / w) * 13) % 256).toByte() })
        val mono = MonoConverter.toMono(gray)
        val bytes = EscPosRaster.encode(mono)
        assertEquals(520, mono.data.size)
        assertEquals("85ac5e66e86ae4760be123b42b6ffe97eb2aa5a8297bd66eb902786ec231d579", sha(mono.data))
        assertEquals(537, bytes.size)
        assertEquals("584c8a21a9fb097dd72d87bac86f6dde22be3bb7ac13417a6b48bebb152b62b0", sha(bytes))
    }
}
