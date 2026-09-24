# DESIGN-013 — skyprint-template đa target + phân phối (DESIGN)

> depends_on REQ-013, DESIGN-009 (engine), DESIGN-012 (phân phối).

## Thay đổi
- `skyprint-template/build.gradle.kts`: thêm `iosArm64()`, `iosSimulatorArm64()`, `android { namespace="com.dcorp.skyprint.template"; compileSdk=36; minSdk=24; withHostTest {} }` (plugin `com.android.kotlin.multiplatform.library`). Code engine hoàn toàn `commonMain` (không `java.*`) nên không cần sửa mã nguồn.
- `implementation(project(":skyprint-core"))` → `api(...)`: type của core lộ ra qua API template (`ReceiptDocument` trong `RenderResult`), và cần để export sang framework Swift.
- **XCFramework chuyển từ core sang template** (framework tổng `Skyprint`, `export(project(":skyprint-core"))`): Swift `import Skyprint` thấy cả `EscPosEncoder` lẫn `TemplateEngine`. Đổi tên từ `SkyprintCore` (chưa từng release, không phá ai).
- `Package.swift` sản phẩm `Skyprint`, `binaryTarget(path: "ios/Skyprint.xcframework.zip")` (~6 MB).
- Flutter: `skyprint-template-android` + publication gốc `skyprint-core` (KMP root module — bắt buộc vì `.module` của template tham chiếu nó) xuất vào `android/repo/` qua repo `FlutterPluginRepo`; `release.sh` kiểm version cả core lẫn template.
- Plugin: method `printOrderReceipt(address, templateJson, dataJson)` → `TemplateParser.parse` → `TemplateValue.fromJson` → `TemplateEngine.render` → `EscPosEncoder.encode` → `UsbPrinterTransport`. Trả về danh sách `warnings`.

## API thật đã xác nhận (giải quyết giả định ở SkyPos-Flutter DESIGN-001)
Kotlin: `TemplateParser.parse(json)`, `TemplateValue.fromJson(json)`, `TemplateEngine.render(template, data, RenderOptions())`.
Swift: `TemplateParser.shared.parse(json:)`, `TemplateValueCompanion.shared.fromJson(json:)`, `MoneyFormat.companion.VND`, `RenderOptions(money:paper:images:)`, `TemplateEngine.shared.render(template:data:options:)`.

## Kiểm chứng
- `:skyprint-template:allTests`: 24/24 trên jvmTest, testAndroidHostTest, iosSimulatorArm64Test.
- `ios/verify` (Swift package): render mẫu JSON `each`+`money` → 4 phần tử PASS trên iOS Simulator.
- SkyPos-Flutter `apps/master` build APK với plugin mới PASS (biên dịch `printOrderReceipt` với type template).

## Risks / open questions
- `printOrderReceipt` mới chỉ biên dịch được, CHƯA in thật trên máy in (cần thiết bị USB Printer-class) và CHƯA kiểm tra gọi từ background isolate (xem SkyPos-Flutter DESIGN-001).
- `ImageResolver` (logo) chưa nối trong plugin (`RenderOptions()` mặc định, không ảnh).
- Phía iOS của plugin Flutter vẫn chưa có.
