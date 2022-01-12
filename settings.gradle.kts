rootProject.name = "interop-proxy-server"

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/projectronin/package-repo")
            credentials {
                username = System.getenv("PACKAGE_USER")
                password = System.getenv("PACKAGE_TOKEN")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
