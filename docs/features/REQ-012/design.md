# DESIGN-012 — Phân phối đa nền tảng: nhúng AAR trong plugin Flutter, XCFramework/SPM cho iOS (DESIGN)

> depends_on REQ-012, DESIGN-011 (publish Maven).

## Nguyên tắc

- **1 nguồn version**: `kmp/build.gradle.kts` (`version = "X.Y.Z"`) là nguồn duy nhất. `flutter/skyprint_flutter/pubspec.yaml` (`version:`), `Package.swift`/podspec và git tag `vX.Y.Z` phải bằng giá trị đó — script release (dưới) kiểm và fail nếu lệch.
- **Không tự cấu hình ở phía app dùng**: mọi thứ app cần đã nằm trong artifact/plugin.

## Giai đoạn 1 — Flutter: nhúng AAR vào plugin

### Cấu trúc

```
flutter/skyprint_flutter/
  pubspec.yaml                      # version = X.Y.Z
  lib/skyprint_flutter.dart
  android/
    build.gradle                    # trỏ repo cục bộ ./repo
    repo/                           # Maven-layout, COMMIT vào git
      com/dcorp/skyprint/skyprint-core-android/X.Y.Z/
        skyprint-core-android-X.Y.Z.aar
        skyprint-core-android-X.Y.Z.pom
        skyprint-core-android-X.Y.Z.module
        (+ .sources.jar nếu có, + maven-metadata nếu Gradle yêu cầu)
    src/main/kotlin/...
  README.md                         # cách dùng + yêu cầu tối thiểu
```

### Xuất AAR vào repo của plugin

Trong `kmp/skyprint-core/build.gradle.kts`, thêm 1 repository cho `publishing` (ngoài repo GitLab của DESIGN-011):

```kotlin
publishing {
    repositories {
        maven {
            name = "FlutterPluginRepo"
            url = uri(rootProject.projectDir.resolve("../flutter/skyprint_flutter/android/repo"))
        }
    }
}
```
Lệnh: `./gradlew :skyprint-core:publishAndroidPublicationToFlutterPluginRepoRepository` (chỉ publication `android`; các dependency transitive như kotlinx-coroutines, ktor, kable, kotlin-stdlib được Gradle kéo từ **mavenCentral/google công khai** theo file `.module/.pom`, không cần nhúng). Trước khi publish, xoá thư mục `repo/com/dcorp/skyprint` cũ để không dồn version cũ.

### Sửa `flutter/skyprint_flutter/android/build.gradle`

- Bỏ khối `maven { url 'https://gitlab.../packages/maven' }` và `mavenLocal()`.
- Thêm (đăng ký cho toàn build, vì `:app` resolve classpath xuyên project và KHÔNG kế thừa `repositories` riêng của plugin — đã gặp thật):
  ```groovy
  def localRepo = uri("${projectDir}/repo")
  rootProject.allprojects {
      repositories {
          maven { url localRepo }
      }
  }
  repositories {
      google()
      mavenCentral()
      maven { url localRepo }
  }
  dependencies {
      implementation 'com.dcorp.skyprint:skyprint-core-android:X.Y.Z'
      implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2'
  }
  ```
- Version `X.Y.Z` trong dependency phải khớp version thư mục trong `repo/` (script release tự sinh/ghi).
- **Bỏ** dòng `mavenLocal()` ở `SkyPos-Flutter/apps/master/android/build.gradle.kts` (đã thêm tạm ở REQ-011) sau khi verify plugin tự đủ; gỡ luôn khối repo GitLab nếu app từng thêm.

### README của plugin (bắt buộc)

Nêu: khối `git:` mẫu; yêu cầu tối thiểu — Kotlin Gradle plugin >= 2.4.10 (vì AAR compile bằng Kotlin 2.4.10; app cũ hơn báo "binary version of metadata is 2.4.0, expected 2.1.0"), `org.jetbrains.kotlin.android` + migrate `kotlinOptions{jvmTarget}` sang `compilerOptions` (Kotlin 2.4 cấm hẳn), compileSdk >= 36, minSdk >= 24, Android only; cách cấp quyền clone repo private.

### Dự án dùng

```yaml
skyprint_flutter:
  git:
    url: <git-url-skyprint>
    path: flutter/skyprint_flutter
    ref: v0.1.0
```

## Giai đoạn 2 — iOS: XCFramework + SPM/CocoaPods

1. Trong `skyprint-core/build.gradle.kts`:
   ```kotlin
   import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
   val xcf = XCFramework("SkyprintCore")
   listOf(iosArm64(), iosSimulatorArm64()).forEach { t ->
       t.binaries.framework { baseName = "SkyprintCore"; isStatic = true; xcf.add(this) }
   }
   ```
   (gộp vào block `kotlin {}` hiện có; đã có sẵn 2 target iOS — hiện mới ra `.klib`, chưa ra framework.) Lệnh: `./gradlew :skyprint-core:assembleSkyprintCoreXCFramework` → `kmp/skyprint-core/build/XCFrameworks/release/SkyprintCore.xcframework`.
2. Cần `export`/api rõ ràng những type Swift cần gọi (EscPosEncoder, model, transport LAN/BLE). Kotlin/Native API sang Swift có quy ước tên riêng (object → `.shared`, suspend → async/completion handler) — cần 1 lượt viết wrapper Swift mỏng nếu API lộ ra khó dùng, đánh giá khi làm.
3. Phân phối: zip XCFramework → đính kèm release (GitLab/GitHub release asset hoặc Generic Package), `Package.swift` ở gốc repo:
   ```swift
   // swift-tools-version:5.9
   import PackageDescription
   let package = Package(
       name: "SkyprintCore",
       platforms: [.iOS(.v15)],
       products: [.library(name: "SkyprintCore", targets: ["SkyprintCore"])],
       targets: [.binaryTarget(name: "SkyprintCore",
                               url: "<release-asset-url>/SkyprintCore-X.Y.Z.xcframework.zip",
                               checksum: "<swift package compute-checksum>")]
   )
   ```
   Dev khai package bằng URL git + tag. Khi dev nội bộ có thể đổi tạm sang `path:` cục bộ.
4. Podspec (tuỳ chọn): `vendored_frameworks = 'SkyprintCore.xcframework'`, dùng chung cho phía iOS của plugin Flutter (`flutter/skyprint_flutter/ios`, hiện chưa có — pubspec chỉ khai `platforms: android`).
5. Rủi ro: BLE (Kable) cần link CoreBluetooth + khai `NSBluetoothAlwaysUsageDescription` ở app; framework tĩnh + Kable/ktor phải kiểm link được từ Xcode sạch. Chưa kiểm chứng.

## Giai đoạn 3 — React Native

Chưa thiết kế. Mở REQ riêng khi có nhu cầu.

## Quy trình release (1 lệnh, dùng chung mọi kênh)

`scripts/release.sh X.Y.Z` (viết mới), thứ tự:
1. Kiểm working tree sạch, đang ở `main`.
2. Set `version = "X.Y.Z"` trong `kmp/build.gradle.kts`; set `version: X.Y.Z` trong `flutter/skyprint_flutter/pubspec.yaml`; sửa version dependency trong `flutter/skyprint_flutter/android/build.gradle`.
3. `cd kmp && ./gradlew clean build` (test phải PASS).
4. `./gradlew :skyprint-core:publish :skyprint-template:publish` (registry Maven, DESIGN-011).
5. Xoá `flutter/skyprint_flutter/android/repo/com/dcorp/skyprint`, chạy `publishAndroidPublicationToFlutterPluginRepoRepository`.
6. (Giai đoạn 2) build XCFramework, zip, tính checksum, cập nhật `Package.swift`.
7. Cập nhật `CHANGELOG.md`; commit `release: vX.Y.Z`; `git tag vX.Y.Z`; push kèm tag.
8. In checklist xác minh (Acceptance criteria).

Script phải fail sớm nếu version 3 nơi (kmp / pubspec / Package.swift) không khớp.

## Dependencies

- Không thêm thư viện. Cần Xcode + macOS runner cho bước XCFramework (publish tay từ máy Mac là đường chính, như REQ-011).

## Risks / open questions

- **`rootProject.allprojects { repositories }` có thể vô hiệu** nếu settings.gradle của app đặt `RepositoriesMode.FAIL_ON_PROJECT_REPOS` (app Flutter mặc định không đặt, nhưng dự án khác có thể). Cần verify ở 2 app thật (SkyPos-Flutter + 1 app Flutter tạo mới) trước khi coi là xong.
- **Dung lượng git**: AAR (vài MB mỗi version) commit vào repo, mỗi release thêm 1 bản → repo phình. Giảm bằng cách chỉ giữ version hiện tại trong `repo/` (xoá bản cũ; lịch sử vẫn trong git). Nếu quá nặng, chuyển sang Git LFS hoặc phân phối AAR qua release asset rồi tải lúc build.
- **Yêu cầu Kotlin >= 2.4.10 phía app** là ràng buộc cứng lên mọi app Flutter dùng plugin (đã phải sửa `settings.gradle.kts` + `kotlinOptions` ở SkyPos-Flutter). Nếu ràng buộc này quá đau cho app khác, hạ Kotlin của skyprint không khả thi (kable-core 0.44+ đã build bằng 2.4.10) — ghi rõ trong README thay vì hứa tương thích rộng.
- `git:` dependency chỉ có tag, không có semver range (`^`). Nếu số app tăng và cần range thì cân nhắc pub server riêng (ngoài phạm vi).
- Wrapper Swift cho API Kotlin/Native: chưa đánh giá độ dễ dùng thực tế (suspend/Result/sealed class map sang Swift kém gọn).
- Giai đoạn 1 chưa có phía `ios/` cho plugin Flutter: app Flutter chạy iOS sẽ không có method nào (plugin khai android-only) — chấp nhận tới khi làm giai đoạn 2 + REQ-001 xong.
