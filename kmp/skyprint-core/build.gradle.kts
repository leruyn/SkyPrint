plugins {
    kotlin("multiplatform")
}

// JVM-only for now (v1 encoder core has no platform dependency) -- android()/iosArm64()
// targets get added when REQ-004/007 (native transports) need them, per docs/architecture.md.
kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}
