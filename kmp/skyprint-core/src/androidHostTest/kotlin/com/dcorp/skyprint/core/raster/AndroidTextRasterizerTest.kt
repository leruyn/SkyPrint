package com.dcorp.skyprint.core.raster

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TEST-UNIT-002c -- [AndroidTextRasterizer] qua Robolectric.
 *
 * **Giới hạn đã xác nhận (2026-09-23), không phải bỏ sót:** đã thử CẢ 2
 * plugin Android cho module KMP này -- `com.android.library` cổ (AGP 8.7.3)
 * và `com.android.kotlin.multiplatform.library` chính thức Google khuyến
 * nghị thay thế (AGP 8.13.1, cấu hình hiện tại) -- kết quả GIỐNG HỆT nhau ở
 * cả 2: pipeline Skia native của Robolectric không kích hoạt trên máy này.
 * Đã xác minh bằng test tay: `measureText` luôn `1.0`, `fontMetrics.bottom
 * - top` luôn `0.0`, `getPixels` luôn toàn số 0. Kết luận: đây là vấn đề
 * môi trường/phiên bản Robolectric (4.14.1) trên máy này, KHÔNG PHẢI do
 * chọn sai plugin Android -- đã loại trừ bằng thực nghiệm, không đoán.
 * File `.dylib` cho `mac/aarch64` có thật trên classpath
 * (`nativeruntime-dist-compat`), nên không phải thiếu binary.
 *
 * Vì vậy bộ test ở đây CHỈ kiểm bất biến không phụ thuộc pixel thật (không
 * throw, kích thước hợp lệ) -- KHÔNG kiểm nội dung glyph/canh lề/độ cao chữ
 * thật. Việc đó cần xác minh trên thiết bị/emulator Android thật (đáng tin
 * cậy nhất) hoặc thử phiên bản Robolectric khác -- theo dõi riêng, không
 * chặn REQ-002 tiếp tục.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidTextRasterizerTest {

    private val rasterizer = AndroidTextRasterizer()

    @Test
    fun `render khong throw va tra dung widthDots`() {
        val bitmap = rasterizer.render(RasterBlock(listOf(RasterLine(listOf(RasterSpan("Hello"))))), widthDots = 384)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun `render document rong khong throw`() {
        val bitmap = rasterizer.render(RasterBlock(emptyList()), widthDots = 200)
        assertEquals(200, bitmap.width)
    }

    @Test
    fun `nhieu dong khong throw va tra ve bitmap hop le`() {
        val block = RasterBlock(
            listOf(
                RasterLine(listOf(RasterSpan("A"))),
                RasterLine(listOf(RasterSpan("B", bold = true))),
                RasterLine(listOf(RasterSpan("C", heightScale = 2))),
            ),
        )
        val bitmap = rasterizer.render(block, widthDots = 384)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 0)
    }
}
