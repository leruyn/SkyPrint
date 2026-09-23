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

/**
 * [width]/[height]: 1..8, khớp 2 nibble độc lập của `GS ! n` (REQ-010 --
 * máy in ESC/POS cho phép phóng ngang/dọc riêng, không bắt buộc bằng nhau
 * như `size` gộp một chỉ số ở bản REQ-001 ban đầu). [inverse]: chữ trắng
 * nền đen (`GS B 1`), dùng cho phiếu huỷ món nổi bật (REQ-010's req.md).
 */
data class TextStyle(
    val align: Align = Align.LEFT,
    val bold: Boolean = false,
    val width: Int = 1,
    val height: Int = 1,
    val inverse: Boolean = false,
    val underline: Boolean = false,
) {
    init {
        require(width in 1..8) { "TextStyle.width phải trong khoảng 1..8, nhận $width" }
        require(height in 1..8) { "TextStyle.height phải trong khoảng 1..8, nhận $height" }
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

    /** [partial]: cắt một phần (giấy còn dính 1 điểm, dễ xé) -- máy không có dao thay bằng feed qua [CapabilityFilter] (REQ-010). */
    data class Cut(val partial: Boolean = true) : Element

    data class Barcode(
        val data: String,
        val type: BarcodeType = BarcodeType.CODE128,
        val heightDots: Int = 80,
        val hri: Boolean = true,
        val align: Align = Align.CENTER,
    ) : Element {
        init {
            require(data.isNotEmpty()) { "Barcode.data không được rỗng" }
            require(heightDots in 1..255) { "Barcode.heightDots phải trong khoảng 1..255, nhận $heightDots" }
            type.validate(data)
        }
    }

    /** Mở két tiền (`ESC p`) -- không in giấy. [pin]: chân kết nối két (0 hoặc 1, tuỳ dây RJ11 đấu vào chân nào). */
    data class Drawer(val pin: Int = 0, val pulseMs: Int = 100) : Element {
        init {
            require(pin == 0 || pin == 1) { "Drawer.pin chỉ có 0 hoặc 1, nhận $pin" }
            require(pulseMs in 1..500) { "Drawer.pulseMs phải trong khoảng 1..500, nhận $pulseMs" }
        }
    }

    /** Còi báo máy in bếp có phiếu mới. */
    data class Beep(val times: Int = 1, val durationMs: Int = 100) : Element {
        init {
            require(times in 1..9) { "Beep.times phải trong khoảng 1..9, nhận $times" }
            require(durationMs in 100..900) { "Beep.durationMs phải trong khoảng 100..900 (bội số 100), nhận $durationMs" }
        }
    }
}

enum class QrErrorCorrection(val code: Int) { L(48), M(49), Q(50), H(51) }

/**
 * Loại mã vạch 1D hỗ trợ (REQ-010). [oldStyleSymbol] khác null -> dùng lệnh
 * ESC/POS cũ `GS k m d1..dk NUL`; null -> dùng lệnh mới `GS k m n d1..dn`
 * (chỉ CODE128 cần, vì phải mang tiền tố code-set `{A/{B/{C` trong data).
 */
enum class BarcodeType(internal val oldStyleSymbol: Int?, internal val newStyleSymbol: Int) {
    UPCA(oldStyleSymbol = 0, newStyleSymbol = 65),
    EAN13(oldStyleSymbol = 2, newStyleSymbol = 67),
    EAN8(oldStyleSymbol = 3, newStyleSymbol = 68),
    CODE39(oldStyleSymbol = 4, newStyleSymbol = 69),
    CODE128(oldStyleSymbol = null, newStyleSymbol = 73);

    internal fun validate(data: String) {
        fun requireDigits(minLen: Int, maxLen: Int, label: String) {
            require(data.length in minLen..maxLen) { "$label cần $minLen hoặc $maxLen chữ số, nhận '$data' (${data.length})" }
            require(data.all { it.isDigit() }) { "$label chỉ nhận chữ số, nhận '$data'" }
        }
        when (this) {
            UPCA -> requireDigits(11, 12, "UPCA")
            EAN13 -> requireDigits(12, 13, "EAN13")
            EAN8 -> requireDigits(7, 8, "EAN8")
            CODE39 -> require(data.isNotEmpty()) { "CODE39 không được rỗng" }
            CODE128 -> require(data.isNotEmpty()) { "CODE128 không được rỗng" }
        }
    }
}

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
