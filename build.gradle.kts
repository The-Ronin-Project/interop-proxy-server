import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateSDLTask
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("com.projectronin.interop.gradle.mockk") version "1.0.0-SNAPSHOT"
    id("com.projectronin.interop.gradle.ktorm") version "1.0.0-SNAPSHOT"
    id("com.projectronin.interop.gradle.ktor") version "1.0.0-SNAPSHOT"
    id("com.projectronin.interop.gradle.spring") version "1.0.0-SNAPSHOT"
    id("com.projectronin.interop.gradle.jackson") version "1.0.0-SNAPSHOT"

    id("org.springframework.boot") version "2.4.5"

    id("com.expediagroup.graphql") version "4.2.0"

    id("com.google.cloud.tools.jib") version "3.1.4"

    id("org.unbroken-dome.test-sets") version "4.0.0"

    `maven-publish`
}

dependencies {
    implementation("com.projectronin.interop:interop-common:${project.property("interopCommonVersion")}")
    implementation("com.projectronin.interop.ehr:interop-ehr:${project.property("interopEhrVersion")}")
    implementation("com.projectronin.interop.ehr:interop-ehr-factory:${project.property("interopEhrVersion")}")
    implementation("com.projectronin.interop.ehr:interop-fhir:${project.property("interopEhrVersion")}")
    implementation("com.projectronin.interop.ehr:interop-tenant:${project.property("interopEhrVersion")}")
    implementation("com.projectronin.interop.queue:interop-queue:${project.property("interopQueueVersion")}")
    implementation("com.projectronin.interop.queue:interop-queue-db:${project.property("interopQueueVersion")}")

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("mysql:mysql-connector-java:8.0.13")

    implementation("com.expediagroup:graphql-kotlin-schema-generator:4.2.0")
    implementation("com.expediagroup:graphql-kotlin-spring-server:4.2.0")

    // Runtime Dependency on each EHR implementation.
    runtimeOnly("com.projectronin.interop.ehr:interop-ehr-epic:${project.property("interopEhrVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test:2.5.5") {
        exclude(module = "mockito-core")
    }
    testImplementation("com.ninja-squad:springmockk:3.0.1")
    testImplementation("com.graphql-java-kickstart:graphql-spring-boot-starter-test:11.1.0") {
        exclude(module = "mockito-core")
    }

    testImplementation("com.projectronin.interop:interop-common-test-db:${project.property("interopCommonVersion")}")
    testImplementation("com.projectronin.interop.ehr:interop-ehr-liquibase:${project.property("interopEhrVersion")}")
    testImplementation("com.projectronin.interop.queue:interop-queue-liquibase:${project.property("interopQueueVersion")}")

    testImplementation("com.projectronin.interop.ehr:interop-ehr-epic:${project.property("interopEhrVersion")}")

    testImplementation("com.beust:klaxon:5.5")

    testImplementation("com.squareup.okhttp3:mockwebserver")

    // Allows us to change environment variables
    testImplementation("org.junit-pioneer:junit-pioneer:1.5.0")
}

testSets {
    "it"()
}

// We need to exclude some dependencies from our it testSet
configurations.getByName("itImplementation") {
    exclude(module = "graphql-spring-boot-starter-test")
}

tasks.getByName<BootRun>("bootRun") {
    // Set the classpath to match our integration tests
    classpath = sourceSets.getByName("it").runtimeClasspath

    // Activate the "it" profile so that we can access the application setup
    args("--spring.profiles.active=it")
}

val graphqlGenerateSDL by tasks.getting(GraphQLGenerateSDLTask::class) {
    packages.set(listOf("com.projectronin.interop.proxy.server"))
    schemaFile.set(file("${project.projectDir}/interopSchema.graphql"))
}

// We want to tie the GraphQL schema generation to the kotlin compile step.
tasks.compileKotlin.get().finalizedBy(graphqlGenerateSDL)

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/projectronin/package-repo")
            credentials {
                username = System.getenv("PACKAGE_USER")
                password = System.getenv("PACKAGE_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("bootJava") {
            artifact(tasks.getByName("bootJar"))
        }
    }
}
