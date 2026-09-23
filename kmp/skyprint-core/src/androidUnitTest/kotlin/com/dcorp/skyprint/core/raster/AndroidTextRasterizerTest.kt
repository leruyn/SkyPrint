package com.dcorp.skyprint.core.raster

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TEST-UNIT-002c -- [AndroidTextRasterizer] qua Robolectric.
 *
 * **Giới hạn đã xác nhận (2026-09-23), không phải bỏ sót:** trong tổ hợp
 * cụ thể của repo này -- `kotlin("multiplatform")` + `com.android.library`
 * cổ (AGP 9 cấm tổ hợp này, xem ghi chú ở `kmp/build.gradle.kts`) + AGP
 * 8.7.3 + Robolectric 4.14.1 -- pipeline vẽ Skia native của Robolectric
 * KHÔNG kích hoạt: `Paint.measureText`/`Canvas.drawText`/`Paint.fontMetrics`
 * đều trả giá trị stub cố định (đã xác minh bằng test tay: `measureText`
 * luôn `1.0`, `getPixels` luôn toàn số 0, bất kể SDK khai trong `@Config`).
 * Đã thử `@Config(sdk=[34])`, `manifest=Config.NONE` -- không đổi kết quả.
 * File `.dylib` cho `mac/aarch64` có thật trên classpath
 * (`nativeruntime-dist-compat`), nên không phải thiếu binary; nghi nhiều
 * khả năng nhất là Robolectric không nhận diện đúng biến thể Android target
 * của KMP để tự tải `android-all-instrumented` đúng SDK request (chỉ thấy
 * SDK rất cũ 14/15/21 trong cache, không thấy SDK 34 được tải dù đã khai).
 *
 * Vì vậy bộ test ở đây CHỈ kiểm bất biến không phụ thuộc pixel thật (không
 * throw, kích thước hợp lệ) -- KHÔNG kiểm nội dung glyph/canh lề/độ cao chữ
 * thật. Việc đó cần xác minh trên thiết bị/emulator Android thật hoặc một
 * cấu hình Robolectric khác (theo dõi riêng, không chặn REQ-002 tiếp tục).
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
