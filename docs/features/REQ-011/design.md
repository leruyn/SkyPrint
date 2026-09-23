# DESIGN-011 — Đồng bộ skyprint lên Git + publish SDK (DESIGN)

> depends_on REQ-011.

## Kiến trúc publish

```
skyprint (git remote mới)
  └─ kmp/skyprint-core/build.gradle.kts  -- maven-publish (đã có từ trước, xem CODE-012)
       publishing {
         repositories {
           maven {
             name = "GitLabDcorp"
             url = uri("https://<gitlab-host>/api/v4/projects/<PROJECT_ID>/packages/maven")
             credentials(HttpHeaderCredentials::class) {
               name = "Job-Token"                     // CI
               value = System.getenv("CI_JOB_TOKEN")
                 ?: System.getenv("GITLAB_DEPLOY_TOKEN") // publish tay, xem "Auth"
             }
             authentication { create<HttpHeaderAuthentication>("header") }
           }
         }
       }
```

KMP tự sinh sẵn 5 publication (`kotlinMultiplatform`, `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`) khi `maven-publish` được apply — **không cần khai tay publication nào**, chỉ cần khai `repositories`.

## Auth

- **CI** (khuyến nghị lâu dài): `CI_JOB_TOKEN` GitLab tự cấp cho mỗi job, không cần lưu secret riêng — chỉ cần project publish có quyền ghi Package Registry của chính nó (mặc định có).
- **Publish tay** (bước 4 của REQ-011, làm trước khi có CI): tạo GitLab **Deploy Token** (scope `write_package_registry`) hoặc **Personal Access Token** cùng scope, export `GITLAB_DEPLOY_TOKEN=<token>` trước khi chạy `./gradlew :skyprint-core:publish`. KHÔNG hard-code token vào `build.gradle.kts` hay commit vào git.

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
    url = uri("https://<gitlab-host>/api/v4/projects/<PROJECT_ID>/packages/maven")
    // CI: HttpHeaderCredentials "Job-Token"/CI_JOB_TOKEN. Máy dev: Deploy Token
    // read_package_registry, KHÔNG commit token vào git — đọc từ
    // gradle.properties cục bộ (gitignored) hoặc biến môi trường.
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
        url 'https://<gitlab-host>/api/v4/projects/<PROJECT_ID>/packages/maven'
        // credentials -- xem cách SkytabOffline làm, KHÔNG commit token vào git
    }
}
dependencies {
    implementation 'com.dcorp.skyprint:skyprint-core-android:0.1.0'
}
```

## CI (optional follow-up, không chặn REQ-011)

`.gitlab-ci.yml` ở repo skyprint, job chạy khi tag `v*`:
```yaml
publish-sdk:
  stage: deploy
  rules:
    - if: '$CI_COMMIT_TAG =~ /^v/'
  script:
    - cd kmp && ./gradlew :skyprint-core:publish
```
Cần runner macOS (Kotlin/Native iOS targets biên dịch trên Linux runner sẽ fail biên dịch iosArm64/iosSimulatorArm64 — xem GitLab macOS runner nội bộ nếu có, hoặc tạm giới hạn CI job chỉ publish variant `android`+`jvm` bằng `./gradlew :skyprint-core:publishAndroidPublicationToGitLabDcorpRepository :skyprint-core:publishJvmPublicationToGitLabDcorpRepository` nếu chưa có runner macOS).

## Dependencies

- Không thêm thư viện mới — `maven-publish` là Gradle built-in plugin, đã thêm ở `skyprint-core/build.gradle.kts` (CODE-012, đã xong).

## Risks / open questions

- **Chưa chốt GitLab project/namespace cụ thể** — REQ-011's Main flow bước 1 cần PM/PO xác nhận trước khi ai đó (kể cả Cursor) tự tạo project mới.
- Runner CI thiếu macOS → publish target iOS từ CI sẽ fail; publish tay từ máy Mac (như hiện tại) vẫn là đường đi chính cho tới khi có runner phù hợp.
- `0.1.0-local` hiện đang được `SkyprintUsbTestActivity`/`skyprint_flutter` tham chiếu cứng — migration 2 consumer (mục trên) phải làm **cùng lúc** với publish thật, nếu không 2 app sẽ gãy build (không tìm thấy `0.1.0-local` trên registry, đúng như thiết kế — registry không có version đó).
