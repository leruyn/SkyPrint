import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
}

// DEPRECATED từ 0.3.0: toàn bộ code raster (ReceiptDocument, ReceiptRasterizer, MonoConverter, EscPosRaster, PrintImage...) đã
// gộp vào skyprint-core, GIỮ NGUYÊN package `com.dcorp.skyprint.raster`. Module này chỉ còn là artifact rỗng kéo theo
// skyprint-core (api) để dự án đang khai `skyprint-raster` không vỡ build; dự án mới chỉ cần `skyprint-core`.
// Sẽ gỡ khi mọi dự án dùng đã bỏ dependency này (xem CHANGELOG).
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
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":skyprint-core"))
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
