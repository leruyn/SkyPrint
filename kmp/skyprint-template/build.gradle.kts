plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

// JVM-only for now, cùng lý do skyprint-core (REQ-009's design: parser dùng
// kotlinx-serialization-json để đọc JSON mẫu in).
kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":skyprint-core"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
