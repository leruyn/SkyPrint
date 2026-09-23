package com.dcorp.skyprint.core.model

/**
 * Khổ giấy máy in nhiệt phổ thông (DESIGN-001). [columns] là số ký tự Font A
 * chuẩn (12x24 dot), dùng để word-wrap/layout trước khi biết thật sự dùng
 * font cỡ nào; [dots] là bề rộng in được tính theo dot, dùng cho raster
 * (REQ-002) và QR.
 */
enum class PaperWidth(val columns: Int, val dots: Int) {
    MM58(columns = 32, dots = 384),
    MM80(columns = 48, dots = 576),
}

enum class Align { LEFT, CENTER, RIGHT }

/** size: 1..8, khớp bit width/height nhân đôi của `GS ! n` (DESIGN-001's EscPosCommands.size dùng bool -- ở đây tổng quát hơn cho REQ-010's Text nhiều cỡ). */
data class TextStyle(
    val align: Align = Align.LEFT,
    val bold: Boolean = false,
    val size: Int = 1,
) {
    init {
        require(size in 1..8) { "TextStyle.size phải trong khoảng 1..8, nhận $size" }
    }
}

/** Một cột trong [Element.Row]. [weight] quyết định tỷ lệ bề rộng, tương tự flex-grow. */
data class Column(
    val text: String,
    val weight: Int = 1,
    val align: Align = Align.LEFT,
    val bold: Boolean = false,
) {
    init {
        require(weight > 0) { "Column.weight phải > 0, nhận $weight" }
    }
}

/**
 * Một phần tử trong [ReceiptDocument]. Tập hợp tối thiểu cho REQ-001 --
 * [Element.QrCode]/[Element.Image] khai interface theo DESIGN-001 nhưng nội
 * dung mã hoá byte thật của QR/ảnh raster nằm ở REQ-002 (raster)/REQ-010
 * (mã vạch, capabilities) khi các CODE đó lên; QrCode ở đây được cài đủ vì
 * nằm trong scope REQ-001 (không thuộc "Out of scope" của req.md).
 */
sealed interface Element {
    data class Text(val text: String, val style: TextStyle = TextStyle()) : Element

    data class Row(val columns: List<Column>) : Element {
        init {
            require(columns.isNotEmpty()) { "Row phải có ít nhất 1 cột" }
        }
    }

    data class Divider(val char: Char = '-') : Element

    /** Feed [lines] dòng trắng. Giá trị âm coi như 0 -- clamp ở [EscPosCommands.feed], không ném lỗi ở đây vì đây chỉ là data class. */
    data class Feed(val lines: Int) : Element

    data class QrCode(
        val data: String,
        val moduleSize: Int = 6,
        val errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
        val align: Align = Align.CENTER,
    ) : Element {
        init {
            require(data.isNotEmpty()) { "QrCode.data không được rỗng" }
            require(moduleSize in 1..16) { "QrCode.moduleSize phải trong khoảng 1..16, nhận $moduleSize" }
        }
    }

    data class Image(val bitmap: MonoBitmap, val align: Align = Align.CENTER) : Element

    data object Cut : Element
}

enum class QrErrorCorrection(val code: Int) { L(48), M(49), Q(50), H(51) }

/** Ảnh 1-bit đã raster sẵn (REQ-002 tạo ra, REQ-001 chỉ cần biết hình dạng để đóng gói `GS v 0`). */
data class MonoBitmap(val width: Int, val height: Int, val bits: ByteArray) {
    init {
        val expectedRowBytes = (width + 7) / 8
        require(bits.size == expectedRowBytes * height) {
            "MonoBitmap.bits sai kích thước: cần ${expectedRowBytes * height} byte cho ${width}x$height, nhận ${bits.size}"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is MonoBitmap && width == other.width && height == other.height && bits.contentEquals(other.bits)

    override fun hashCode(): Int = 31 * (31 * width + height) + bits.contentHashCode()
}

enum class TextMode { ASCII, RASTER }

data class ReceiptDocument(val paper: PaperWidth, val elements: List<Element>)
