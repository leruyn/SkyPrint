package com.dcorp.skyprint.raster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrintImageTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun GrayBitmap.px(x: Int, y: Int) = pixels[y * width + x].toInt() and 0xFF

    @Test
    fun trong_suot_duoc_ghep_len_nen_trang() {
        val g = PrintImage.toGray(intArrayOf(0), 1, 1, tone = PrintTone.Neutral) // 0x00000000 = trong suốt
        assertEquals(255, g.px(0, 0))
    }

    @Test
    fun den_dac_van_den_trang_van_trang_voi_tong_neutral() {
        val g = PrintImage.toGray(intArrayOf(argb(255, 0, 0, 0), argb(255, 255, 255, 255)), 2, 1, tone = PrintTone.Neutral)
        assertEquals(0, g.px(0, 0))
        assertEquals(255, g.px(1, 0))
    }

    @Test
    fun dung_be_rong_thi_giu_nguyen_khong_lay_mau_lai() {
        val src = IntArray(8 * 3) { argb(255, it * 10 % 256, it * 10 % 256, it * 10 % 256) }
        val g = PrintImage.toGray(src, 8, 3, targetWidth = 8, tone = PrintTone.Neutral)
        assertEquals(8, g.width)
        assertEquals(3, g.height)
        for (i in src.indices) assertEquals(i * 10 % 256, g.pixels[i].toInt() and 0xFF, "điểm $i")
    }

    @Test
    fun co_nho_giu_ti_le_va_lay_trung_binh() {
        // 4x2: nửa trái đen, nửa phải trắng -> co về 2 rộng: 1 đen, 1 trắng; cao 1
        val b = argb(255, 0, 0, 0)
        val w = argb(255, 255, 255, 255)
        val g = PrintImage.toGray(intArrayOf(b, b, w, w, b, b, w, w), 4, 2, targetWidth = 2, tone = PrintTone.Neutral)
        assertEquals(2, g.width)
        assertEquals(1, g.height)
        assertEquals(0, g.px(0, 0))
        assertEquals(255, g.px(1, 0))
    }

    @Test
    fun tong_thermal_khop_nguong_152_cua_skytab_android() {
        // Khớp skytab-android: contrast 1,18 rồi ngưỡng 152. Xám 140 -> 142 < 152 = đen; xám 150 -> 154 >= 152 = trắng;
        // sau khi dời 24 để dùng ngưỡng 128 của MonoConverter, đen phải < 128 và trắng >= 128.
        val den = PrintImage.toGray(intArrayOf(argb(255, 140, 140, 140)), 1, 1, tone = PrintTone.Thermal)
        assertTrue(den.px(0, 0) < 128, "xám 140 phải ra chấm đen, nhưng là ${den.px(0, 0)}")
        val trang = PrintImage.toGray(intArrayOf(argb(255, 150, 150, 150)), 1, 1, tone = PrintTone.Thermal)
        assertTrue(trang.px(0, 0) >= 128, "xám 150 phải ra trắng, nhưng là ${trang.px(0, 0)}")
    }

    @Test
    fun anh_xam_ra_dung_lenh_in_qua_mono_va_escpos() {
        val g = PrintImage.toGray(IntArray(16 * 4) { argb(255, 0, 0, 0) }, 16, 4)
        val bytes = EscPosRaster.encode(MonoConverter.toMono(g))
        assertTrue(bytes.size > 16 * 4 / 8)
    }
}
