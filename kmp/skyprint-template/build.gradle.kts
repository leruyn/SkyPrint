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
            name = "GitLabDcorp"
            url = uri("https://gitlab.skyxtech.com.vn/api/v4/projects/116/packages/maven")
            val token = System.getenv("CI_JOB_TOKEN")
                ?: System.getenv("GITLAB_DEPLOY_TOKEN")
                ?: System.getenv("GITLAB_TOKEN")
                ?: (project.findProperty("gitlab.token") as? String)
                ?: ""

            val headerKey = when {
                System.getenv("CI_JOB_TOKEN") != null -> "Job-Token"
                System.getenv("GITLAB_DEPLOY_TOKEN") != null -> "Deploy-Token"
                else -> "Private-Token"
            }

            credentials(HttpHeaderCredentials::class) {
                name = headerKey
                value = token
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

