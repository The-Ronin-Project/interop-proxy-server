rootProject.name = "interop-proxy-server"

pluginManagement {
    val interopGradleVersion = "2.0.0"
    plugins {
        id("com.projectronin.interop.gradle.spring") version interopGradleVersion
        id("com.projectronin.interop.gradle.integration") version interopGradleVersion

        id("org.springframework.boot") version "2.7.2"
        id("com.expediagroup.graphql") version "6.1.0"
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
