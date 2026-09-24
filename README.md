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

### iOS (Swift, Swift Package Manager)

XCFramework tĩnh `Skyprint` = skyprint-core + skyprint-template (iosArm64 + Simulator arm64) nằm trong repo (`ios/`), khai qua SPM bằng URL git + tag:

```swift
// Package.swift của app, hoặc Xcode: File > Add Package Dependencies...
.package(url: "https://github.com/leruyn/SkyPrint.git", from: "0.1.1")
// target: .product(name: "Skyprint", package: "SkyPrint")
```

```swift
import Skyprint

let doc = ReceiptDocument(paper: PaperWidth.mm80, elements: [
    ElementText(text: "SKYPOS", style: TextStyle(align: Align.center, bold: true, width: 2, height: 2, inverse: false, underline: false)),
    ElementCut(partial: true),
])
let bytes = EscPosEncoder.shared.encode(document: doc, mode: TextMode.ascii,
                                        buzzerCommand: BuzzerCommand.escB, rasterizer: nil)
```

Lưu ý: iOS chỉ có transport LAN + BLE (không USB/Bluetooth Classic). Kotlin/Native không giữ default
argument, phải truyền đủ tham số như trên. App dùng BLE cần khai `NSBluetoothAlwaysUsageDescription`.
Render mẫu JSON: `TemplateParser.shared.parse(json:)` → `TemplateEngine.shared.render(template:data:options:)`
(xem `ios/verify`). Tối thiểu iOS 15. Repo private: máy dev/CI cần quyền clone.

### Flutter

Plugin Android-only nằm ở [flutter/skyprint_flutter](flutter/skyprint_flutter) (MethodChannel bọc
`skyprint-core-android`, lấy từ Maven registry). Hiện mới có `listUsbCandidates`/`printUsbTest`
(test tay) và `printOrderReceipt(templateJson, dataJson)` (mẫu JSON → in USB, REQ-013). Dùng trong app Flutter:

```yaml
dependencies:
  skyprint_flutter:
    git:
      url: <git-url-skyprint>
      path: flutter/skyprint_flutter
```
(Trước khi có remote git: `path: <đường dẫn tới skyprint>/flutter/skyprint_flutter`.)

## Đóng góp / báo lỗi

Nội bộ Dcorp — báo qua kênh dự án đang dùng, hoặc sửa trực tiếp kèm cập nhật
`docs/features/<REQ>/req.md` + test (xem `.claude/`/`AGENTS.md` cho quy trình
V-model).
