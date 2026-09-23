plugins {
    kotlin("multiplatform") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    // AGP 9.x cấm kết hợp com.android.library với kotlin.multiplatform (bắt dùng
    // com.android.kotlin.multiplatform.library, plugin mới chưa rõ có hỗ trợ
    // Robolectric/androidUnitTest đầy đủ như classic com.android.library không) --
    // ghim 8.7.3, bản cuối còn cho phép tổ hợp cũ, đã có sẵn trong cache.
    id("com.android.library") version "8.7.3" apply false
}
