import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
}

// P2 của docs/SkyPrint-SDK-ConsolidationPlan.md (SkytabOffline): danh sách máy in đã cấu hình (CRUD, mặc định mỗi loại, resolve),
// thuần commonMain, KHÔNG phụ thuộc mô hình nghiệp vụ của app nào. Chuyển từ `PrinterRegistry` của sky-printer.
kotlin {
    jvm()

    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.dcorp.skyprint.registry"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":skyprint-core"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
        }
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
