# REQ-012 — Phân phối skyprint đa nền tảng theo cách import chuẩn của từng hệ sinh thái (REQ)

## Actor(s)

- **Dev dự án khác của Dcorp** (KMP/Android, Flutter, iOS Swift, sau này có thể React Native): khai 1 dòng dependency, không phải tự cấu hình registry/credentials/đường dẫn.
- **PM/PO / release owner**: chạy quy trình release 1 lần, ra đủ artifact cho mọi kênh cùng version.

## Bối cảnh

skyprint là SDK dùng chung nhiều dự án Dcorp. REQ-011 đã giải quyết kênh **Kotlin/Maven** (7 package trên registry, khai `implementation("com.dcorp.skyprint:skyprint-core:0.1.0")`). Các kênh còn lại chưa "cắm là chạy":

- **Flutter**: plugin `flutter/skyprint_flutter` mới dùng được qua `path:`. Plugin phụ thuộc `skyprint-core-android` trên Maven **private** — mỗi app dùng plugin phải tự khai URL registry + token trong Gradle của nó (đã gặp thật: `:app` resolve classpath xuyên project nên không kế thừa `repositories` riêng của plugin, xem lịch sử REQ-011). Không giống thư viện phổ thông.
- **iOS Swift thuần**: chưa có artifact nào (Kotlin/Native chỉ ra `.klib`, không import được từ Swift).
- **React Native**: có app RN trong Dcorp (redplus, fwgroup, skyorder-app) nhưng chưa có nhu cầu in xác nhận → chưa làm.
- Không hệ sinh thái nào (GitHub Packages / GitLab Package Registry) có registry kiểu pub (Dart) → không thể có `skyprint_flutter: ^0.1.0` mà không dựng pub server riêng.

## Goal

Mỗi hệ sinh thái import skyprint bằng đúng cách quen thuộc, **cùng 1 version** (SemVer), 1 quy trình release:

| Kênh | Cách import | Giai đoạn |
|---|---|---|
| Kotlin/KMP/Android | `implementation("com.dcorp.skyprint:skyprint-core:X.Y.Z")` | Đã xong (REQ-011) |
| Flutter | `git:` dependency + tag `vX.Y.Z`, **không cần khai thêm gì ở Gradle của app** | Giai đoạn 1 |
| iOS Swift | Swift Package Manager (`.binaryTarget` XCFramework) hoặc CocoaPods | Giai đoạn 2 |
| React Native | `npm i @dcorp/skyprint` | Giai đoạn 3 — CHƯA quyết, chỉ ghi nhận |

## Preconditions

- REQ-011 hoàn tất: skyprint có remote git thật + đã publish `0.1.0` lên registry Maven.
- Repo skyprint có thể clone được từ máy/CI của các dự án dùng (SSH key hoặc token read-only).

## Main flow

### Giai đoạn 1 — Flutter `git:` + tag (nhúng sẵn AAR trong plugin)

1. Release owner chạy quy trình release (DESIGN-012 mục "Quy trình release"): publish Maven, **đồng thời** xuất `skyprint-core-android` (aar + pom + module) vào Maven-layout repo cục bộ **nằm trong plugin** (`flutter/skyprint_flutter/android/repo/`).
2. Commit thư mục `repo/` vào git (chấp nhận file nhị phân trong repo để plugin tự đủ; xem Risk về dung lượng).
3. `flutter/skyprint_flutter/android/build.gradle` chỉ trỏ repo cục bộ đó (bỏ URL registry GitLab + `mavenLocal()`); đăng ký repo cho toàn build qua `rootProject.allprojects { repositories { ... } }` để `:app` resolve được classpath xuyên project.
4. Dev app Flutter khai:
   ```yaml
   skyprint_flutter:
     git:
       url: <git-url-skyprint>
       path: flutter/skyprint_flutter
       ref: vX.Y.Z
   ```
   rồi `flutter pub get` + build — không sửa file Gradle nào của app, không token Maven.
5. Yêu cầu tối thiểu phía app được ghi rõ trong README của plugin (Kotlin Gradle plugin >= 2.4.10, compileSdk >= 36, minSdk >= 24) vì AAR build bằng Kotlin 2.4.10 (đã gặp thật ở REQ-011: app Kotlin 2.1.0 báo "metadata 2.4.0, expected 2.1.0").

### Giai đoạn 2 — iOS Swift (XCFramework + SPM/CocoaPods)

1. Cấu hình `skyprint-core` xuất framework tĩnh `SkyprintCore` (iosArm64 + iosSimulatorArm64) và gộp thành XCFramework.
2. Thêm `Package.swift` (binaryTarget) ở gốc repo skyprint; XCFramework zip đính kèm release/tag (URL + checksum) hoặc đường dẫn cục bộ khi dev.
3. Dev iOS thêm package qua SPM bằng URL git + tag `vX.Y.Z`.
4. (Tuỳ chọn) podspec cho CocoaPods + phía iOS của plugin Flutter (`flutter/skyprint_flutter/ios`) dùng chung XCFramework này.

### Giai đoạn 3 — React Native (placeholder)

Chỉ ghi nhận. Chỉ mở REQ riêng khi có app RN cần in xác nhận; hướng dự kiến: native module bọc `skyprint-core-android` + XCFramework, phát hành npm.

## Alternate / exception flows

- Repo skyprint private + dev không có quyền clone: `git:` dependency lỗi ở `flutter pub get` — README phải nêu cách cấp quyền (SSH key/deploy token), đây là thiết lập một lần, không phải lỗi thiết kế.
- Muốn `skyprint_flutter: ^X.Y.Z` (semver range) thay vì tag: cần pub server riêng (Cloudsmith/Artifactory/unpub) — ngoài phạm vi REQ này, ghi nhận là hướng nâng cấp sau nếu số app dùng tăng mạnh.
- `rootProject.allprojects { repositories }` không có hiệu lực trên 1 số cấu hình Gradle của app (vd `RepositoriesMode.FAIL_ON_PROJECT_REPOS` trong settings): README plugin nêu cách khai tay 1 dòng thay thế.

## Postconditions

- Tạo app Flutter mới từ đầu, thêm đúng 1 khối `git:` vào pubspec, build APK debug thành công **trên máy chưa từng chạy `publishToMavenLocal`** và không có cấu hình Maven private.
- Version của mọi kênh khớp nhau và khớp tag git.

## Out of scope

- Pub server riêng / pub.dev.
- Maven Central (đã loại từ REQ-011).
- Phía iOS của USB transport (iOS không hỗ trợ USB).
- Giai đoạn 3 (React Native).

## Acceptance criteria (feeds TEST-UAT-012)

- [ ] Giai đoạn 1: app Flutter mới (không phải SkyPos-Flutter), chỉ thêm khối `git:` ở trên, `flutter build apk --debug` PASS trên máy không có `~/.m2` chứa skyprint và không có token Maven.
- [ ] Giai đoạn 1: `SkyPos-Flutter/apps/master` chuyển từ `path:` sang `git:` + tag, build vẫn PASS.
- [ ] Giai đoạn 2: 1 app iOS/Xcode mới thêm package SPM bằng URL git + tag, `import SkyprintCore` và gọi được `EscPosEncoder` build cho cả Simulator lẫn thiết bị.
- [ ] `CHANGELOG.md` + README (Kotlin/Flutter/iOS) cập nhật cách import + yêu cầu tối thiểu (Kotlin/AGP/compileSdk/iOS deployment target).
- [ ] Release checklist chạy từ đầu tới cuối ra đủ artifact với cùng 1 version, không sửa tay file nào giữa chừng.
