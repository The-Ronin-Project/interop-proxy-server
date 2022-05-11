rootProject.name = "interop-proxy-server"

pluginManagement {
    val interopGradleVersion = "1.0.0-SNAPSHOT"
    plugins {
        id("com.projectronin.interop.gradle.jackson") version interopGradleVersion
        id("com.projectronin.interop.gradle.ktor") version interopGradleVersion
        id("com.projectronin.interop.gradle.ktorm") version interopGradleVersion
        id("com.projectronin.interop.gradle.mockk") version interopGradleVersion
        id("com.projectronin.interop.gradle.spring") version interopGradleVersion
        id("com.projectronin.interop.gradle.integration") version interopGradleVersion

        id("org.springframework.boot") version "2.6.7"
        id("com.expediagroup.graphql") version "5.4.0"
        id("com.google.cloud.tools.jib") version "3.2.1"
    }

    repositories {
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }
        }
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-releases/")
            mavenContent {
                releasesOnly()
            }
        }
        maven {
            url = uri("https://repo.devops.projectronin.io/repository/maven-public/")
            mavenContent {
                releasesOnly()
            }
        }
        mavenLocal()
        gradlePluginPortal()
    }
}
