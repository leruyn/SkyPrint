# DESIGN-011 — Đồng bộ skyprint lên Git + publish SDK (DESIGN)

> **Ghi chú thực tế (2026-09-24):** registry đã chọn là **GitHub Packages** (`https://maven.pkg.github.com/leruyn/SkyPrint`, repo `leruyn/SkyPrint`), không phải GitLab như mô tả bên dưới; CI nằm ở `.github/workflows/publish.yml`. Đọc "GitLab" bên dưới là "GitHub". Đọc package cần token `read:packages`.

> depends_on REQ-011.

## Kiến trúc publish

```
skyprint (GitHub: https://github.com/leruyn/SkyPrint.git)
  └─ kmp/skyprint-core/build.gradle.kts  -- maven-publish (đã có từ trước, xem CODE-012)
       publishing {
         repositories {
           maven {
             name = "GitHubPackages"
             url = uri("https://maven.pkg.github.com/leruyn/SkyPrint")
             credentials {
               username = System.getenv("GITHUB_ACTOR") ?: (project.findProperty("gpr.user") as? String) ?: "leruyn"
               password = System.getenv("GITHUB_TOKEN") ?: (project.findProperty("gpr.key") as? String) ?: ""
             }
           }
         }
       }
```

KMP tự sinh sẵn 5 publication (`kotlinMultiplatform`, `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`) khi `maven-publish` được apply — **không cần khai tay publication nào**, chỉ cần khai `repositories`.

## Auth

- **CI** (GitHub Actions): `GITHUB_TOKEN` do GitHub Actions tự cấp cho mỗi run thông qua `secrets.GITHUB_TOKEN` với permission `packages: write`.
- **Publish tay** (bước 4 của REQ-011, làm trước khi có CI): tạo GitHub **Personal Access Token (classic hoặc fine-grained)** với scope `write:packages` (và `read:packages`), export `GITHUB_ACTOR=<username>` và `GITHUB_TOKEN=<pat>` hoặc cấu hình trong `~/.gradle/gradle.properties`:
  ```properties
  gpr.user=<github-username>
  gpr.key=<github-pat>
  ```
  trước khi chạy `./gradlew :skyprint-core:publish` (hoặc `publishAllPublicationsToGitHubPackagesRepository`). KHÔNG hard-code token vào `build.gradle.kts` hay commit vào git.

## Version

- `kmp/build.gradle.kts`: `version = "0.1.0-local"` → `version = "0.1.0"` (bỏ hậu tố `-local`, đánh dấu lần publish thật đầu tiên).
- Từ lần publish thứ 2 trở đi: bump theo SemVer — README.md đã ghi rõ lý do ("đổi API ảnh hưởng tất cả nơi phụ thuộc"), patch/minor cho thay đổi tương thích ngược, major khi đổi API breaking (vd đổi signature `PrinterTransport`/`EscPosEncoder`).

## Migration 2 consumer

### SkytabOffline

`settings.gradle.kts` — bỏ:
```kotlin
includeBuild("../../../skyprint/kmp")
```
Thay bằng (trong `dependencyResolutionManagement.repositories`):
```kotlin
maven {
    url = uri("https://maven.pkg.github.com/leruyn/SkyPrint")
    credentials {
        username = System.getenv("GITHUB_ACTOR") ?: (extra.properties["gpr.user"] as? String) ?: "leruyn"
        password = System.getenv("GITHUB_TOKEN") ?: (extra.properties["gpr.key"] as? String) ?: ""
    }
}
```
`skytab/build.gradle.kts` — đổi:
```kotlin
implementation("com.dcorp.skyprint:skyprint-core")          // includeBuild: coordinate KHÔNG cần version
```
thành:
```kotlin
implementation("com.dcorp.skyprint:skyprint-core-android:0.1.0")   // registry thật: PHẢI có version
```
(giữ nguyên toàn bộ `SkyprintUsbTestActivity.kt`/manifest — không đổi gì phía code, chỉ đổi cách resolve dependency.)

### SkyPos-Flutter (`skyprint_flutter/android/build.gradle`)

Đổi:
```groovy
repositories {
    google()
    mavenCentral()
    mavenLocal()   // <- bỏ dòng này
}
dependencies {
    implementation 'com.dcorp.skyprint:skyprint-core-android:0.1.0-local'   // <- đổi version
}
```
thành:
```groovy
repositories {
    google()
    mavenCentral()
    maven {
        url 'https://maven.pkg.github.com/leruyn/SkyPrint'
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR") ?: "leruyn"
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN") ?: ""
        }
    }
}
dependencies {
    implementation 'com.dcorp.skyprint:skyprint-core-android:0.1.0'
}
```

## CI (GitHub Actions)

`.github/workflows/publish.yml` ở repo skyprint, job chạy khi tag `v*` hoặc manual dispatch:
```yaml
name: CI & Publish SDK

on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  check:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: cd kmp && ./gradlew check

  publish:
    needs: check
    if: startsWith(github.ref, 'refs/tags/v') || github.event_name == 'workflow_dispatch'
    runs-on: macos-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: cd kmp && ./gradlew :skyprint-core:publish :skyprint-template:publish
```
Sử dụng runner `macos-latest` giúp build và publish đầy đủ cả Android, JVM lẫn iOS targets (`iosArm64`, `iosSimulatorArm64`) trơn tru.

## Dependencies

- Không thêm thư viện mới — `maven-publish` là Gradle built-in plugin, đã thêm ở `skyprint-core/build.gradle.kts` (CODE-012, đã xong).

## Risks / open questions

- `0.1.0-local` hiện đang được `SkyprintUsbTestActivity`/`skyprint_flutter` tham chiếu cứng — migration 2 consumer (mục trên) phải làm **cùng lúc** với publish thật, nếu không 2 app sẽ gãy build (không tìm thấy `0.1.0-local` trên registry, đúng như thiết kế — registry không có version đó).
- Client tải artifact từ GitHub Packages Maven repository cần có GitHub credentials (PAT có quyền `read:packages`), do GitHub Packages yêu cầu xác thực kể cả với public/internal repo.
