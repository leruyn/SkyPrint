package com.dcorp.skyprint.raster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** UNIT-053-11: ngắt dòng theo độ rộng đo được (từ, rồi ký tự khi từ quá dài). */
class TextWrapTest {
    private val w = { s: String -> s.length * 10 }   // mỗi ký tự rộng 10

    @Test
    fun `ngat theo tu khong vuot be rong`() {
        val lines = TextWrap.wrap("Bún bò Huế đặc biệt", 90, w)
        assertEquals(listOf("Bún bò", "Huế đặc", "biệt"), lines)
        assertTrue(lines.all { w(it) <= 90 })
    }

    @Test
    fun `tu qua dai bi cat theo ky tu van giu du ky tu`() {
        val lines = TextWrap.wrap("Supercalifragilistic", 80, w)
        assertEquals(listOf("Supercal", "ifragili", "stic"), lines)
        assertEquals("Supercalifragilistic", lines.joinToString(""))
    }

    @Test
    fun `chuoi rong va xuong dong san`() {
        assertEquals(listOf(""), TextWrap.wrap("", 100, w))
        assertEquals(listOf("a", "b"), TextWrap.wrap("a\nb", 100, w))
        assertEquals(listOf("vừa đủ"), TextWrap.wrap("vừa đủ", 60, w))
    }
}
