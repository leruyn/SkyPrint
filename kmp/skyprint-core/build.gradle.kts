import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

// androidTarget() thêm ở CODE-002c (REQ-002's AndroidTextRasterizer) --
// androidUnitTest chạy trên JVM qua Robolectric (shadow Android framework),
// không cần thiết bị/emulator thật để test Canvas/StaticLayout. iosArm64()
// thêm khi tới REQ-002's iOS CoreText/REQ-004../007 cần transport thật.
kotlin {
    jvm()
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.robolectric:robolectric:4.14.1")
                implementation("androidx.test:core:1.6.1")
            }
        }
    }
}

android {
    namespace = "com.dcorp.skyprint.core"
    // AGP 8.7.3 (bản cuối còn hỗ trợ kotlin.multiplatform + com.android.library, xem
    // ghi chú ở kmp/build.gradle.kts) mới test tới compileSdk 35 -- SkytabOffline dùng
    // AGP 9.1.0/compileSdk 36 nhưng module đó KHÔNG kết hợp trực tiếp KMP+android.library
    // theo cách này. Nâng lên 36 khi chuyển sang com.android.kotlin.multiplatform.library.
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // isReturnDefaultValues: method Android chưa được Robolectric shadow đầy đủ trả
    // giá trị mặc định (0/null/false) thay vì throw -- an toàn cho unit test thuần
    // Canvas/StaticLayout, không cần bật includeAndroidResources (không dùng resource).
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}
