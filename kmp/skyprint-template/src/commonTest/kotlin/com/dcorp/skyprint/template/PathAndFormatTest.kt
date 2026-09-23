package com.dcorp.skyprint.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathResolverTest {
    private val scope = Scope.root(
        TemplateValue.of(
            mapOf(
                "store" to mapOf("name" to "SkyPos", "logo" to null),
                "order" to mapOf("total" to 50000, "discount" to 0),
                "items" to listOf(
                    mapOf("name" to "Phở bò", "modifiers" to listOf(mapOf("name" to "Không hành"))),
                    mapOf("name" to "Trà đá", "modifiers" to emptyList<Any>()),
                ),
            ),
        ),
    )

    @Test
    fun `resolve path don gian`() {
        assertEquals(TemplateValue.Str("SkyPos"), PathResolver.resolve(scope, "store.name"))
    }

    @Test
    fun `resolve path co index mang`() {
        assertEquals(TemplateValue.Str("Phở bò"), PathResolver.resolve(scope, "items[0].name"))
        assertEquals(TemplateValue.Str("Không hành"), PathResolver.resolve(scope, "items[0].modifiers[0].name"))
    }

    @Test
    fun `path thieu tra ve Null khong throw`() {
        assertEquals(TemplateValue.Null, PathResolver.resolve(scope, "order.tax"))
        assertEquals(TemplateValue.Null, PathResolver.resolve(scope, "items[9].name"))
    }

    @Test
    fun `bien vong lap che field goc cung ten`() {
        val bound = scope.bind("it" to TemplateValue.Str("bound-value"))
        assertEquals(TemplateValue.Str("bound-value"), PathResolver.resolve(bound, "it"))
    }

    @Test
    fun `truthy dung quy uoc null rong 0 false la falsy`() {
        assertFalse(TemplateValue.Null.isTruthy())
        assertFalse(TemplateValue.Str("").isTruthy())
        assertFalse(TemplateValue.Num(0.0).isTruthy())
        assertFalse(TemplateValue.Bool(false).isTruthy())
        assertTrue(TemplateValue.Str("x").isTruthy())
        assertTrue(TemplateValue.Num(1.0).isTruthy())
    }
}

class InterpolatorTest {
    private fun scope(vararg pairs: Pair<String, Any?>) = Scope.root(TemplateValue.of(mapOf(*pairs)))

    @Test
    fun `thay 1 placeholder trong chuoi co chu bao quanh`() {
        val result = Interpolator.interpolate(scope("name" to "SkyPos"), "Xin chào {{name}}!", MoneyFormat.VND)
        assertEquals("Xin chào SkyPos!", result)
    }

    @Test
    fun `nhieu placeholder trong 1 chuoi`() {
        val result = Interpolator.interpolate(scope("a" to "1", "b" to "2"), "{{a}}-{{b}}", MoneyFormat.VND)
        assertEquals("1-2", result)
    }

    @Test
    fun `formatter money chia 100 va ngan cach nghin`() {
        val result = Interpolator.interpolate(scope("total" to 1234500), "{{total | money}}", MoneyFormat.VND)
        assertEquals("12.345", result)
    }

    @Test
    fun `formatter upper`() {
        val result = Interpolator.interpolate(scope("s" to "pho bo"), "{{s | upper}}", MoneyFormat.VND)
        assertEquals("PHO BO", result)
    }

    @Test
    fun `formatter default khi gia tri rong`() {
        val result = Interpolator.interpolate(scope("logo" to null), "{{logo | default:'(không có)'}}", MoneyFormat.VND)
        assertEquals("(không có)", result)
    }

    @Test
    fun `formatter datetime`() {
        val result = Interpolator.interpolate(
            scope("t" to "2026-09-23T14:05:07"),
            "{{t | datetime:'HH:mm dd/MM/yyyy'}}",
            MoneyFormat.VND,
        )
        assertEquals("14:05 23/09/2026", result)
    }

    @Test
    fun `chuoi cac formatter noi tiep nhau`() {
        val result = Interpolator.interpolate(scope("s" to "phở bò"), "{{s | upper | truncate:3}}", MoneyFormat.VND)
        assertEquals("PHỞ", result)
    }
}
