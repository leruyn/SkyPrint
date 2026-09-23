plugins {
    kotlin("multiplatform")
}

// JVM-only for now (v1 encoder core has no platform dependency) -- android()/iosArm64()
// targets get added when REQ-004/007 (native transports) need them, per docs/architecture.md.
kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
