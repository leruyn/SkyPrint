package com.dcorp.skyprint.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Dữ liệu chứng từ đưa vào [TemplateEngine.render] (REQ-009, DESIGN-009).
 * Cây giá trị tối giản kiểu JSON -- không phải Map/List trần để [PathResolver]
 * và [ConditionEvaluator] xử lý thống nhất một loại, không phải downcast
 * `Any?` rải rác khắp engine.
 */
sealed interface TemplateValue {
    data class Str(val value: String) : TemplateValue
    data class Num(val value: Double) : TemplateValue
    data class Bool(val value: Boolean) : TemplateValue
    data class Arr(val items: List<TemplateValue>) : TemplateValue
    data class Obj(val fields: Map<String, TemplateValue>) : TemplateValue
    data object Null : TemplateValue

    companion object {
        fun of(value: Any?): TemplateValue = when (value) {
            null -> Null
            is TemplateValue -> value
            is String -> Str(value)
            is Boolean -> Bool(value)
            is Int -> Num(value.toDouble())
            is Long -> Num(value.toDouble())
            is Double -> Num(value)
            is Float -> Num(value.toDouble())
            is Map<*, *> -> Obj(value.entries.associate { (k, v) -> k.toString() to of(v) })
            is List<*> -> Arr(value.map { of(it) })
            else -> Str(value.toString())
        }

        fun fromJson(json: String): TemplateValue = fromJsonElement(Json.parseToJsonElement(json))

        private fun fromJsonElement(element: JsonElement): TemplateValue = when (element) {
            is JsonNull -> Null
            is JsonObject -> Obj(element.entries.associate { (k, v) -> k to fromJsonElement(v) })
            is JsonArray -> Arr(element.map { fromJsonElement(it) })
            is JsonPrimitive -> when {
                element.isString -> Str(element.content)
                element.booleanOrNull != null -> Bool(element.boolean)
                else -> Num(element.doubleOrNull ?: 0.0)
            }
        }
    }
}

/** Truthy theo quy ước [ConditionEvaluator] (DESIGN-009): không null, không rỗng, khác 0, khác false. */
fun TemplateValue.isTruthy(): Boolean = when (this) {
    TemplateValue.Null -> false
    is TemplateValue.Str -> value.isNotEmpty()
    is TemplateValue.Num -> value != 0.0
    is TemplateValue.Bool -> value
    is TemplateValue.Arr -> items.isNotEmpty()
    is TemplateValue.Obj -> true
}

/** Chuỗi hiển thị mặc định khi nội suy vào text (chưa qua formatter nào). */
fun TemplateValue.displayString(): String = when (this) {
    TemplateValue.Null -> ""
    is TemplateValue.Str -> value
    is TemplateValue.Bool -> value.toString()
    is TemplateValue.Num -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    is TemplateValue.Arr -> items.joinToString(", ") { it.displayString() }
    is TemplateValue.Obj -> fields.toString()
}
