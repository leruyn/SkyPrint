package com.dcorp.skyprint_flutter

import androidx.annotation.NonNull
import com.dcorp.skyprint.core.encode.EscPosEncoder
import com.dcorp.skyprint.core.model.Align
import com.dcorp.skyprint.core.model.Column
import com.dcorp.skyprint.core.model.Element
import com.dcorp.skyprint.core.model.PaperWidth
import com.dcorp.skyprint.core.model.PrinterInfo
import com.dcorp.skyprint.core.model.ReceiptDocument
import com.dcorp.skyprint.core.model.TextMode
import com.dcorp.skyprint.core.model.TextStyle
import com.dcorp.skyprint.core.model.TransportKind
import com.dcorp.skyprint.core.usb.UsbPrinterTransport
import com.dcorp.skyprint.template.RenderOptions
import com.dcorp.skyprint.template.TemplateEngine
import com.dcorp.skyprint.template.TemplateParser
import com.dcorp.skyprint.template.TemplateValue
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Cầu Dart -> skyprint-core.UsbPrinterTransport. CHỈ để test USB song song
 * với pos_data's UsbPrinterService (flutter_usb_printer) đang chạy thật --
 * KHÔNG đụng/thay code đó, xem skyprint/docs/research/pos-printing-business.md
 * mục "Flutter: ReceiptBuilder hard-code 1 mẫu" cho hướng thay thế sau này.
 */
class SkyprintFlutterPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var appContext: android.content.Context
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "skyprint_flutter/usb")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "listUsbCandidates" -> result.success(listCandidates())
            "printUsbTest" -> printTest(call, result)
            "printOrderReceipt" -> printOrderReceipt(call, result)
            else -> result.notImplemented()
        }
    }

    private fun listCandidates(): List<Map<String, String>> =
        UsbPrinterTransport.listCandidates(appContext).map { info ->
            mapOf("id" to info.id, "name" to info.name, "address" to info.address)
        }

    private fun printTest(call: MethodCall, result: Result) {
        val address = call.argument<String>("address")
        val name = call.argument<String>("name") ?: "USB printer"
        if (address == null) {
            result.error("MISSING_ADDRESS", "Thiếu 'address' (dạng 'vendorId:productId')", null)
            return
        }
        val printer = PrinterInfo(id = "usb:$address", name = name, kind = TransportKind.USB, address = address)

        scope.launch {
            try {
                val doc = ReceiptDocument(
                    paper = PaperWidth.MM80,
                    elements = listOf(
                        Element.Text("SKYPRINT USB TEST", TextStyle(align = Align.CENTER, bold = true, width = 2, height = 2)),
                        Element.Divider(),
                        Element.Row(listOf(Column("Transport", weight = 4), Column("USB (Flutter)", weight = 4, align = Align.RIGHT))),
                        Element.Row(listOf(Column("Thiết bị", weight = 4), Column(name, weight = 4, align = Align.RIGHT))),
                        Element.Cut(),
                    ),
                )
                val bytes = EscPosEncoder.encode(doc, TextMode.ASCII)
                val transport = UsbPrinterTransport(appContext)
                val connection = transport.open(printer)
                try {
                    connection.write(bytes)
                    result.success("In thành công tới $name.")
                } finally {
                    connection.close()
                }
            } catch (e: Exception) {
                result.error("PRINT_FAILED", e.message ?: e::class.simpleName, null)
            }
        }
    }

    // REQ-013: mẫu JSON + dữ liệu JSON thô từ Dart -> render (skyprint-template) -> ESC/POS -> USB.
    private fun printOrderReceipt(call: MethodCall, result: Result) {
        val address = call.argument<String>("address")
        val templateJson = call.argument<String>("templateJson")
        val dataJson = call.argument<String>("dataJson")
        if (address == null || templateJson == null || dataJson == null) {
            result.error("MISSING_ARGUMENT", "Cần 'address', 'templateJson', 'dataJson'", null)
            return
        }
        val name = call.argument<String>("name") ?: "USB printer"
        val printer = PrinterInfo(id = "usb:$address", name = name, kind = TransportKind.USB, address = address)

        scope.launch {
            try {
                val template = TemplateParser.parse(templateJson)
                val rendered = TemplateEngine.render(template, TemplateValue.fromJson(dataJson), RenderOptions())
                val bytes = EscPosEncoder.encode(rendered.document, TextMode.ASCII)
                val connection = UsbPrinterTransport(appContext).open(printer)
                try {
                    connection.write(bytes)
                    result.success(rendered.warnings.map { it.toString() })
                } finally {
                    connection.close()
                }
            } catch (e: Exception) {
                result.error("PRINT_FAILED", e.message ?: e::class.simpleName, null)
            }
        }
    }
}
