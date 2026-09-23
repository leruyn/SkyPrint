plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
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

