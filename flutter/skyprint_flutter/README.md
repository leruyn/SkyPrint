# skyprint_flutter

Cầu nối Flutter -> `skyprint-core` (Kotlin) qua MethodChannel. **Android only** (USB Printer-class).
AAR `skyprint-core-android` đã nhúng sẵn trong `android/repo/` — app không cần khai registry/token Maven.

## Cài đặt

```yaml
# pubspec.yaml của app
dependencies:
  skyprint_flutter:
    git:
      url: https://github.com/leruyn/SkyPrint.git
      path: flutter/skyprint_flutter
      ref: v0.1.0        # tag release, KHÔNG dùng branch
```

Repo private: máy dev/CI cần quyền clone (SSH key hoặc `git config` credential/token read-only).

## Yêu cầu tối thiểu phía app (bắt buộc)

AAR build bằng Kotlin 2.4.10. App dùng phải:

1. **Kotlin Gradle plugin >= 2.4.10** — `android/settings.gradle(.kts)`:
   `id("org.jetbrains.kotlin.android") version "2.4.10" apply false`
   (mặc định template Flutter là 2.1.0 -> lỗi *"binary version of its metadata is 2.4.0, expected version is 2.1.0"*).
2. **Bỏ `kotlinOptions { jvmTarget = ... }`** (Kotlin 2.4 cấm hẳn) trong `android/app/build.gradle(.kts)`:
   ```kotlin
   kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
   ```
3. compileSdk >= 36, minSdk >= 24.

Đã kiểm chứng: app Flutter tạo mới chỉ cần 3 bước trên + khai dependency là build được.

Nếu `settings.gradle` của app đặt `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, thêm tay:
`maven { url = uri("<đường dẫn plugin>/android/repo") }` vào `dependencyResolutionManagement.repositories`.

## API (Dart)

- `SkyprintFlutter.listUsbCandidates()` — liệt kê máy in USB Printer-class.
- `SkyprintFlutter.printUsbTest(candidate)` — in phiếu test.

Method in hoá đơn thật: xem `docs/features/REQ-011`, SkyPos-Flutter `docs/features/REQ-001`.

## Bảo trì (chủ repo skyprint)

`android/repo/` được sinh bởi `scripts/release.sh` — không sửa tay.
