plugins {
    // 2.4.10 -- kable-core 0.44.3+/0.45.0 build bằng compiler 2.4.10 (ABI 2.4.0),
    // Kotlin 2.3.0 không đọc được KLIB đó. Nâng lên để khớp ABI (xem REQ-007).
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    // com.android.library cổ bị AGP 9+ cấm dùng chung với kotlin.multiplatform
    // (xem lịch sử ở CHANGELOG "REQ-002 (thêm)") -- chuyển hẳn sang plugin chính
    // thức Google khuyến nghị cho KMP. 9.1.0 -- khớp AGP của SkytabOffline
    // (consumer qua includeBuild): composite build dùng chung 1 classloader
    // AGP cho mọi included build, lệch version là build fail cứng
    // ("Using multiple versions of the Android Gradle plugin... is not
    // allowed"), không phải lỗi code.
    id("com.android.kotlin.multiplatform.library") version "9.1.0" apply false
}

// Coordinate cho dependency substitution khi dự án khác dùng includeBuild()
// (composite build tạm, chưa publish -- xem README.md "Dùng trong dự án
// khác"). Không có group ở đây thì Gradle không khớp được
// "com.dcorp.skyprint:skyprint-core" với project(":skyprint-core") cục bộ.
allprojects {
    group = "com.dcorp.skyprint"
    version = "0.1.3"
}
