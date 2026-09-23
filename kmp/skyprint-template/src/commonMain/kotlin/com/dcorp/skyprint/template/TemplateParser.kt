package com.dcorp.skyprint.template

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Đọc JSON mẫu in thành [Template] (REQ-009, DESIGN-009). Lỗi cú pháp/JSON hỏng -> [TemplateException] (INVALID_TEMPLATE), không throw lỗi JSON thô của thư viện parse. */
object TemplateParser {
    fun parse(json: String): Template {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            throw TemplateException(TemplateErrorCode.INVALID_TEMPLATE, "JSON mẫu không hợp lệ: ${e.message}")
        }
        val obj = root as? JsonObject ?: throw invalid("gốc phải là JSON object")

        return Template(
            id = obj.stringOrThrow("id"),
            version = obj.intOrThrow("version"),
            documentType = obj.stringOrThrow("document"),
            paper = obj["paper"]?.jsonPrimitiveOrNull()?.contentOrNull,
            elements = (obj["elements"] as? JsonArray ?: throw invalid("thiếu 'elements' hoặc không phải mảng"))
                .map { parseNode(it as? JsonObject ?: throw invalid("phần tử trong 'elements' phải là object")) },
        )
    }

    private fun parseNode(obj: JsonObject): TemplateNode {
        val type = obj.stringOrThrow("type")
        return TemplateNode(
            type = type,
            enabled = obj["enabled"]?.jsonPrimitiveOrNull()?.booleanOrNull ?: true,
            each = obj["each"]?.jsonPrimitiveOrNull()?.contentOrNull,
            asVar = obj["as"]?.jsonPrimitiveOrNull()?.contentOrNull,
            ifExpr = obj["if"]?.jsonPrimitiveOrNull()?.contentOrNull,
            value = obj["value"]?.jsonPrimitiveOrNull()?.contentOrNull,
            style = (obj["style"] as? JsonObject)?.let { parseStyle(it) },
            columns = (obj["columns"] as? JsonArray)?.map { parseColumn(it as JsonObject) },
            header = obj["header"]?.jsonPrimitiveOrNull()?.booleanOrNull ?: false,
            char = obj["char"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "-",
            lines = obj["lines"]?.jsonPrimitiveOrNull()?.intOrNull ?: 1,
            partial = obj["partial"]?.jsonPrimitiveOrNull()?.booleanOrNull ?: true,
            moduleSize = obj["moduleSize"]?.jsonPrimitiveOrNull()?.intOrNull ?: 6,
            errorCorrection = obj["errorCorrection"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "M",
            caption = obj["caption"]?.jsonPrimitiveOrNull()?.contentOrNull,
            barcodeType = obj["barcodeType"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "CODE128",
            heightDots = obj["heightDots"]?.jsonPrimitiveOrNull()?.intOrNull ?: 80,
            hri = obj["hri"]?.jsonPrimitiveOrNull()?.booleanOrNull ?: true,
            pin = obj["pin"]?.jsonPrimitiveOrNull()?.intOrNull ?: 0,
            pulseMs = obj["pulseMs"]?.jsonPrimitiveOrNull()?.intOrNull ?: 100,
            times = obj["times"]?.jsonPrimitiveOrNull()?.intOrNull ?: 1,
            durationMs = obj["durationMs"]?.jsonPrimitiveOrNull()?.intOrNull ?: 100,
            elements = (obj["elements"] as? JsonArray)?.map { parseNode(it as JsonObject) },
            align = obj["align"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "CENTER",
        )
    }

    private fun parseStyle(obj: JsonObject): TemplateStyle {
        val size = obj["size"]?.jsonPrimitiveOrNull()?.intOrNull
        return TemplateStyle(
            align = obj["align"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "LEFT",
            bold = obj["bold"]?.jsonPrimitiveOrNull()?.booleanOrNull ?: false,
            width = size ?: obj["width"]?.jsonPrimitiveOrNull()?.intOrNull ?: 1,
            height = size ?: obj["height"]?.jsonPrimitiveOrNull()?.intOrNull ?: 1,
            inverse = obj["inverse"]?.jsonPrimitiveOrNull()?.booleanOrNull ?: false,
            underline = obj["underline"]?.jsonPrimitiveOrNull()?.booleanOrNull ?: false,
        )
    }

    private fun parseColumn(obj: JsonObject): TemplateColumn = TemplateColumn(
        text = obj.stringOrThrow("text"),
        weight = obj["weight"]?.jsonPrimitiveOrNull()?.intOrNull ?: 1,
        align = obj["align"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "LEFT",
        bold = obj["bold"]?.jsonPrimitiveOrNull()?.booleanOrNull ?: false,
    )

    private fun invalid(reason: String) = TemplateException(TemplateErrorCode.INVALID_TEMPLATE, "Mẫu in không hợp lệ: $reason")

    private fun JsonObject.stringOrThrow(key: String): String =
        this[key]?.jsonPrimitiveOrNull()?.contentOrNull ?: throw invalid("thiếu trường bắt buộc '$key'")

    private fun JsonObject.intOrThrow(key: String): Int =
        this[key]?.jsonPrimitiveOrNull()?.intOrNull ?: throw invalid("thiếu trường số nguyên bắt buộc '$key'")

    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
}
