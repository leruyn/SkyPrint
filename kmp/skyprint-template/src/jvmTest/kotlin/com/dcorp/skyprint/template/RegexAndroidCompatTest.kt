package com.dcorp.skyprint.template

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regex trên Android chạy bằng ICU, KHÁC JVM: ICU ném PatternSyntaxException với `}` hoặc `]` "trần" (không escape,
 * ngoài `[...]` và không phải đóng `{n,m}`) trong khi JVM chấp nhận -- nên test JVM/Android-host không bắt được và app
 * crash lúc engine khởi tạo (sự cố thật: Interpolator `\{\{...}}` crash ngay lần in đầu trên ACE3).
 * Test này quét cú pháp mọi `Regex("""...""")` trong commonMain để chặn tái diễn.
 */
class RegexAndroidCompatTest {
    private val sourceDir = File("src/commonMain/kotlin")

    @Test
    fun `moi Regex trong commonMain khong co dau dong tran (tuong thich ICU cua Android)`() {
        val files = sourceDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "không tìm thấy nguồn tại ${sourceDir.absolutePath}")

        val problems = mutableListOf<String>()
        var checked = 0
        for (file in files) {
            for (m in Regex("""Regex\(\s*${"\"\"\""}(.*?)${"\"\"\""}""", RegexOption.DOT_MATCHES_ALL).findAll(file.readText())) {
                checked++
                bareClosers(m.groupValues[1]).forEach { problems += "${file.name}: '${m.groupValues[1]}' -- $it" }
            }
        }
        assertTrue(checked > 0, "không quét được pattern nào -- test hỏng")
        assertTrue(problems.isEmpty(), "Regex không tương thích Android/ICU:\n" + problems.joinToString("\n"))
    }

    @Test
    fun `bo quet phat hien dung loi cu`() {
        assertTrue(bareClosers("""\{\{\s*(.*?)\s*}}""").isNotEmpty(), "phải bắt được `}}` trần của Interpolator cũ")
        assertTrue(bareClosers("""\[\d+]""").isNotEmpty(), "phải bắt được `]` trần")
        assertTrue(bareClosers("""\{\{\s*(.*?)\s*\}\}""").isEmpty())
        assertTrue(bareClosers("""^(\d{4})-[T ]([^|}\s]+)\[\d+\]""").isEmpty(), "{4}, } trong [...] và \\] là hợp lệ")
    }

    private fun bareClosers(pattern: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        var inClass = false
        var openBrace = false
        while (i < pattern.length) {
            when (val c = pattern[i]) {
                '\\' -> i++ // bỏ ký tự được escape
                '[' -> if (!inClass) inClass = true
                ']' -> if (inClass) inClass = false else out += "']' trần tại $i"
                '{' -> if (!inClass) openBrace = true
                '}' -> if (!inClass) { if (openBrace) openBrace = false else out += "'}' trần tại $i" }
                else -> {}
            }
            i++
        }
        return out
    }
}
