plugins {
    kotlin("multiplatform") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    // com.android.library cổ bị AGP 9+ cấm dùng chung với kotlin.multiplatform
    // (xem lịch sử ở CHANGELOG "REQ-002 (thêm)") -- chuyển hẳn sang plugin chính
    // thức Google khuyến nghị cho KMP. Cần AGP >= 8.10.0; ghim 8.13.1 (bản mới
    // nhất < 9.0 có sẵn trong cache, để còn dùng cú pháp `android {}` như
    // AGP 9 sau này thay vì `androidLibrary {}` đã deprecated từ 9.1.0-alpha09).
    id("com.android.kotlin.multiplatform.library") version "8.13.1" apply false
}
