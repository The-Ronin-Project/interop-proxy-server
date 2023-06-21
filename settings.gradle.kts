rootProject.name = "interop-proxy-server"

pluginManagement {
    plugins {
        id("com.projectronin.interop.gradle.spring") version "3.0.0"
        id("com.projectronin.interop.gradle.spring-boot") version "3.0.0"
        id("com.projectronin.interop.gradle.docker-integration") version "3.0.0"
        id("com.expediagroup.graphql") version "6.5.3"
        id("org.owasp.dependencycheck") version "8.2.1"
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
