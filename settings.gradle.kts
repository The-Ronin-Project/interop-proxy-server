rootProject.name = "interop-proxy-server"

pluginManagement {
    val interopGradleVersion: String by settings
    plugins {
        id("com.projectronin.interop.gradle.jackson") version interopGradleVersion
        id("com.projectronin.interop.gradle.ktor") version interopGradleVersion
        id("com.projectronin.interop.gradle.ktorm") version interopGradleVersion
        id("com.projectronin.interop.gradle.mockk") version interopGradleVersion
        id("com.projectronin.interop.gradle.spring") version interopGradleVersion
        id("com.projectronin.interop.gradle.integration") version interopGradleVersion
    }

    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/projectronin/package-repo")
            credentials {
                username = System.getenv("PACKAGE_USER")
                password = System.getenv("PACKAGE_TOKEN")
            }
        }
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
