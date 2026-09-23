# skyprint

Thư viện in hoá đơn ESC/POS đa nền tảng (Android + iOS), dùng chung cho các
dự án Dcorp — hiện tại: SkytabOffline (KMP), SkyPos-Flutter. SDK nội bộ,
không phải mã nguồn public.

- Kiến trúc, quyết định thiết kế, ma trận transport: [docs/architecture.md](docs/architecture.md)
- Đặc tả từng requirement (use case + design): [docs/features/](docs/features)
- Trạng thái hiện tại (REQ/DESIGN/CODE/TEST): [docs/wbs.md](docs/wbs.md)
- Nghiệp vụ in POS + cách cấu hình mẫu: [docs/research/pos-printing-business.md](docs/research/pos-printing-business.md)
- Thay đổi theo version: [CHANGELOG.md](CHANGELOG.md)

## Dùng trong dự án khác

Chưa publish lên registry (đang ở [Unreleased]). Trước khi phụ thuộc vào
skyprint từ một dự án Dcorp khác, đọc CHANGELOG.md và kiểm SemVer — đây là
thư viện dùng chung nhiều dự án, đổi API ảnh hưởng tất cả nơi phụ thuộc.

### KMP

```kotlin
// settings.gradle.kts của dự án dùng skyprint
dependencyResolutionManagement { repositories { /* GitHub Packages nội bộ, xem architecture.md hoặc docs/features/REQ-011/design.md */ } }

// build.gradle.kts của module cần in
dependencies {
    implementation("com.dcorp.skyprint:skyprint-core:<version>")
}
```

```kotlin
import com.dcorp.skyprint.core.encode.EscPosEncoder
import com.dcorp.skyprint.core.model.*

val doc = ReceiptDocument(
    paper = PaperWidth.MM80,
    elements = listOf(
        Element.Text("SKYPOS", TextStyle(align = Align.CENTER, bold = true, width = 2, height = 2)),
        Element.Divider(),
        Element.Row(listOf(Column("Phở bò", weight = 6), Column("50.000", weight = 3, align = Align.RIGHT))),
        Element.Cut(),
    ),
)
val bytes: ByteArray = EscPosEncoder.encode(doc, TextMode.ASCII)
```

### Flutter

Chưa có (chờ CODE tương ứng ở phía Dart — xem docs/wbs.md).

## Đóng góp / báo lỗi

Nội bộ Dcorp — báo qua kênh dự án đang dùng, hoặc sửa trực tiếp kèm cập nhật
`docs/features/<REQ>/req.md` + test (xem `.claude/`/`AGENTS.md` cho quy trình
V-model).
