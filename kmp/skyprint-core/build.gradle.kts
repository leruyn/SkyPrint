import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // publishToMavenLocal -- SkyPos-Flutter (Gradle/AGP khác hẳn, xem
    // apps/master/android) không dùng chung được includeBuild() như
    // SkytabOffline; consume qua mavenLocal() thay vì gộp build graph.
    `maven-publish`
}

// Plugin chính thức Google khuyến nghị cho Android target trong module KMP
// (thay com.android.library cổ -- AGP 9+ cấm kết hợp cũ với kotlin.multiplatform,
// xem CHANGELOG "REQ-002 (thêm)" và ghi chú ở kmp/build.gradle.kts).
// androidHostTest = unit test chạy trên JVM qua Robolectric (tên mới của
// androidUnitTest ở plugin cũ) -- KHÔNG bật mặc định, phải khai withHostTest.
kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.dcorp.skyprint.core"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("io.ktor:ktor-network:3.4.1") // REQ-005 -- socket TCP đa nền tảng (Android + iOS)
            implementation("com.juul.kable:kable-core:0.45.0") // REQ-007 -- BLE đa nền tảng (Android + iOS)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.robolectric:robolectric:4.14.1")
                implementation("androidx.test:core:1.6.1")
            }
        }
    }
}
