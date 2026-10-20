package com.dcorp.skyprint.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** contentDots = bề rộng ẢNH nên dùng khi raster (bội số của 8, nhỏ hơn dots): 80mm là 512, không phải 576, vì nhiều máy in 80mm thật cắt mép. */
class PaperWidthTest {
    @Test
    fun contentDots_khop_gia_tri_da_chay_tren_may_in_that() {
        assertEquals(360, PaperWidth.MM58.contentDots)
        assertEquals(512, PaperWidth.MM80.contentDots)
    }

    @Test
    fun contentDots_la_boi_so_cua_8_va_khong_vuot_dots() {
        for (p in PaperWidth.entries) {
            assertTrue(p.contentDots % 8 == 0, "${p.name}: ${p.contentDots}")
            assertTrue(p.contentDots <= p.dots)
        }
    }
}
