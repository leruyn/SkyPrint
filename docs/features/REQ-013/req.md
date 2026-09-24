# REQ-013 — skyprint-template chạy trên Android + iOS (REQ)

## Actor(s)
- **Dev app KMP/Android (SkytabOffline)**, **dev app Flutter (SkyPos-Flutter)**, **dev iOS Swift**: render hoá đơn từ mẫu JSON cấu hình được, không hard-code.

## Bối cảnh
`skyprint-template` (REQ-009) trước đây chỉ build `jvm()` ("JVM-only for now") nên KHÔNG dùng được trên Android/iOS. Đây là dependency chặn của SkytabOffline REQ-024 và SkyPos-Flutter REQ-001 (thay thư viện in cũ bằng mẫu in cấu hình được).

## Goal
`skyprint-template` build và chạy đúng như nhau trên jvm, Android, iosArm64, iosSimulatorArm64; có mặt trong mọi kênh phân phối (Maven, plugin Flutter, XCFramework Swift).

## Main flow
1. Module `skyprint-template` khai thêm target Android (plugin `com.android.kotlin.multiplatform.library`) + iosArm64 + iosSimulatorArm64.
2. Toàn bộ 24 test hiện có chạy PASS trên cả 4 target (cùng hành vi).
3. Maven: publish đủ `skyprint-template-android`, `-iosarm64`, `-iossimulatorarm64`, `-jvm`.
4. Flutter: plugin nhúng `skyprint-template-android` (+ artifact gốc `skyprint-core`) trong `android/repo/`; method `printOrderReceipt(templateJson, dataJson)`.
5. iOS: XCFramework tổng `Skyprint` (core + template) — Swift `TemplateParser`/`TemplateValue.fromJson`/`TemplateEngine.render`.

## Alternate / exception flows
- Mẫu sai cú pháp/biến lạ: `TemplateException`/`warnings` như REQ-009 (không đổi).

## Out of scope
- Schema dữ liệu hoá đơn + bộ mẫu mặc định (thuộc app, xem SkytabOffline REQ-024 / SkyPos-Flutter REQ-001).
- Nơi lưu/đồng bộ file mẫu JSON (quyết định của app).

## Acceptance criteria (feeds TEST-UAT-013)
- [ ] `./gradlew :skyprint-template:allTests` PASS: jvmTest + testAndroidHostTest + iosSimulatorArm64Test, mỗi target 24/24.
- [ ] Swift (iOS Simulator) render mẫu JSON có `each`/`money` ra đúng số phần tử.
- [ ] App Flutter build APK với plugin có `skyprint-template-android` không cần cấu hình Maven.
