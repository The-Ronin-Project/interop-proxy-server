rootProject.name = "interop-proxy-server"

pluginManagement {
    plugins {
        id("com.projectronin.interop.gradle.spring") version "2.0.2"
        id("com.projectronin.interop.gradle.integration") version "2.0.2"

        id("org.springframework.boot") version "2.7.3"
        id("com.expediagroup.graphql") version "6.2.5"
        id("com.google.cloud.tools.jib") version "3.3.0"
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
