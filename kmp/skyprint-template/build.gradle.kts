import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
}

// REQ-013: đủ target như skyprint-core (jvm/android/iOS) -- code hoàn toàn commonMain
// (parser dùng kotlinx-serialization-json để đọc JSON mẫu in, không phụ thuộc java.*).
kotlin {
    jvm()

    // REQ-012 gd2 + REQ-013: framework tĩnh tổng "Skyprint" cho Swift = skyprint-core (export)
    // + skyprint-template -- `./gradlew :skyprint-template:assembleSkyprintXCFramework`.
    val skyprintXcf = XCFramework("Skyprint")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Skyprint"
            isStatic = true
            export(project(":skyprint-core"))
            skyprintXcf.add(this)
        }
    }

    android {
        namespace = "com.dcorp.skyprint.template"
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
        // Cùng repo nhúng trong plugin Flutter với skyprint-core (REQ-012/REQ-013).
        maven {
            name = "FlutterPluginRepo"
            url = uri(rootProject.projectDir.resolve("../flutter/skyprint_flutter/android/repo"))
        }
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

