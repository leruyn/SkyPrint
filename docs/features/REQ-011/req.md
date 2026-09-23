# REQ-011 — Đồng bộ skyprint lên Git + publish SDK (Maven Package Registry) (REQ)

## Actor(s)

- **PM/PO**: chạy lệnh git/CI, xác nhận version.
- **Consumer repo** (SkytabOffline, SkyPos-Flutter's `skyprint_flutter` plugin): kéo skyprint-core làm dependency thật thay vì composite build/mavenLocal tạm.

## Bối cảnh (tại sao cần)

skyprint hiện là repo Git **local, chưa có remote** (`git remote -v` rỗng), toàn bộ thay đổi REQ-004..011 (USB/LAN/BT/BLE transport, Kotlin 2.4.10, BLE platform split, AGP 9.1.0, maven-publish) đang nằm **uncommitted trên máy 1 người**. Hai dự án tiêu thụ đang dùng 2 cơ chế tạm khác nhau vì không thể dùng chung 1 cách:

- **SkytabOffline**: `includeBuild("../../../skyprint/kmp")` — do Gradle build local, chỉ chạy được nếu người khác/CI có đúng path tương đối này trên máy họ. Không portable.
- **SkyPos-Flutter**: `mavenLocal()` — chỉ đọc được artifact `publishToMavenLocal` đã chạy trên **cùng máy**. Máy khác/CI build lại sẽ lỗi `Could not find com.dcorp.skyprint:skyprint-core-android`.

Cả 2 cách đều **không dùng được từ máy thứ 2 hoặc CI** cho tới khi skyprint có remote Git thật + artifact publish lên nơi máy khác/CI đọc được.

## Goal

skyprint trở thành SDK nội bộ dùng được từ bất kỳ máy nào trong team (không phụ thuộc path tuyệt đối/tương đối cục bộ, không phụ thuộc `~/.m2` cục bộ của 1 máy), có version rõ ràng theo SemVer.

## Preconditions

- Có 1 GitLab project trống (hoặc repo Git nội bộ tương đương) để làm remote cho skyprint.
- Có quyền tạo/ghi vào GitLab Package Registry của project đó (hoặc quyền admin để bật).

## Main flow

1. PM/PO: tạo GitLab project rỗng cho `skyprint` (không init README từ GitLab để tránh conflict).
2. PM/PO: `git remote add origin <url>`, review lại toàn bộ `git status` hiện tại (nhiều file uncommitted — xem "Bối cảnh"), commit theo từng REQ đã hoàn thành (KHÔNG gộp 1 commit khổng lồ — xem Acceptance criteria), rồi `git push -u origin main`.
3. PM/PO (hoặc CI): cấu hình `publishing.repositories` trong `kmp/skyprint-core/build.gradle.kts` (và `skyprint-template`) trỏ tới GitLab Package Registry của project vừa tạo (Maven repository, auth qua `CI_JOB_TOKEN` trong CI hoặc Personal/Deploy Token lúc chạy tay).
4. PM/PO: bump `version` ở `kmp/build.gradle.kts` từ `0.1.0-local` sang version thật đầu tiên (đề xuất `0.1.0`), publish thật: `./gradlew :skyprint-core:publish` (không phải `publishToMavenLocal`).
5. Consumer (SkytabOffline): thay `includeBuild("../../../skyprint/kmp")` trong `settings.gradle.kts` bằng repository GitLab Package Registry + `implementation("com.dcorp.skyprint:skyprint-core:0.1.0")` theo đúng README's "Mức 2" (xem DESIGN-011 mục Migration).
6. Consumer (SkyPos-Flutter's `skyprint_flutter/android/build.gradle`): thay `mavenLocal()` bằng cùng GitLab Package Registry, coordinate y hệt SkytabOffline.

## Alternate / exception flows

- Chưa có GitLab project sẵn / chưa quyết được host nội bộ nào: tạm dừng ở bước 1, KHÔNG tự ý tạo project mới nếu chưa được xác nhận tên/namespace.
- CI chưa sẵn sàng (chưa có `.gitlab-ci.yml`): publish tay bước 4 vẫn hợp lệ để bắt đầu dùng ngay, CI job là việc làm thêm sau (xem DESIGN-011 mục CI), không chặn REQ này.
- Nếu quyết định KHÔNG dùng GitLab Package Registry (vd dùng GitHub Packages/Nexus khác): toàn bộ flow trên vẫn đúng, chỉ đổi URL/credential ở bước 3+5+6.

## Postconditions

- `git clone` skyprint từ máy sạch + build `./gradlew build` chạy được không cần chỉnh gì.
- SkytabOffline và SkyPos-Flutter build được trên máy KHÔNG có source code skyprint nằm sẵn ở path cục bộ nào (chỉ cần network tới registry).
- `CHANGELOG.md` của skyprint có entry version `0.1.0` liệt kê đúng các REQ đã đưa vào lần publish đầu.

## Out of scope

- Maven Central / công khai ra ngoài Dcorp (README đã ghi: "chỉ nếu sau này mở public").
- CI tự động publish mỗi lần merge (REQ riêng nếu cần, xem DESIGN-011 mục CI ghi rõ đây là optional follow-up).
- Đồng bộ `skyprint-template` (module JVM-only, hiện chưa cần cho 2 app Flutter/KMP đang tích hợp USB) — publish cùng lúc cho tiện nhưng không phải trọng tâm REQ này.

## Acceptance criteria (feeds TEST-UAT-012)

- [ ] `git log` trên remote GitLab có lịch sử commit chia theo REQ (không phải 1 commit "add everything"), review được từng thay đổi độc lập.
- [ ] `git clone` skyprint ra thư mục mới, `cd kmp && ./gradlew build` chạy PASS không cần sửa file nào.
- [ ] Từ máy/thư mục khác (khác máy đã publish), `./gradlew build` của SkytabOffline chạy được sau khi đổi sang coordinate GitLab Package Registry, KHÔNG cần `includeBuild`.
- [ ] Từ máy/thư mục khác, `flutter build apk` của SkyPos-Flutter's `apps/master` chạy được sau khi đổi `skyprint_flutter/android/build.gradle` sang GitLab Package Registry, KHÔNG cần `publishToMavenLocal` trước đó trên máy đó.
- [ ] `CHANGELOG.md` cập nhật đúng version đã publish.
