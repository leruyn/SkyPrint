import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
}

// P1 của docs/SkyPrint-SDK-ConsolidationPlan.md (SkytabOffline): bộ dàn trang + vẽ bill theo ĐIỂM ẢNH (font tỉ lệ, dấu tiếng Việt)
// -> ảnh xám -> Floyd–Steinberg -> lệnh GS v 0. Chuyển nguyên từ module `sky-printer` của SkytabOffline; chủ ý KHÔNG phụ thuộc
// skyprint-core ở pha này (tên ReceiptDocument/MonoBitmap trùng nhưng khác kiểu) -- việc hợp nhất để pha sau, sau khi so được golden.
// Khác skyprint-core ở chỗ: chỉ có 3 backend đồ hoạ (Java2D / Canvas / CoreGraphics), không có transport hay ktor/kable.
kotlin {
    jvm()

    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.dcorp.skyprint.raster"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTest {}
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/leruyn/SkyPrint")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: (project.findProperty("gpr.user") as? String)
                    ?: "leruyn"
                password = System.getenv("GITHUB_TOKEN")
                    ?: (project.findProperty("gpr.key") as? String)
                    ?: ""
            }
        }
    }
}
