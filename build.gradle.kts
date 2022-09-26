import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateSDLTask

plugins {
    java
    `maven-publish`
    id("com.projectronin.interop.gradle.spring")
    id("com.projectronin.interop.gradle.integration")
    id("org.springframework.boot")
    id("com.expediagroup.graphql")
}

dependencies {
    implementation(libs.interop.aidbox)
    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.ehr.api)
    implementation(libs.interop.fhir)
    implementation(libs.interop.queue.api)
    implementation(libs.interop.tenant)
    implementation(libs.interop.ehr.fhir.ronin)
    implementation(libs.interop.commonJackson)

    implementation(platform(libs.kotlinx.coroutines.bom))

    implementation(platform(libs.spring.boot.parent)) {
        exclude(group = "org.jetbrains.kotlinx")
    }
    // Pull in just the security dependencies we need, as we are not using the full security suite.
    implementation(libs.bundles.spring.security)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation(libs.ktor.client.core)
    implementation(libs.bundles.graphql)
    implementation(libs.bundles.hl7v2)

    runtimeOnly(libs.bundles.ehr.impls)
    runtimeOnly(libs.interop.queue.db)
    runtimeOnly(libs.mysql.connector.java)

    // Needed to format logs for DataDog
    runtimeOnly(libs.logstash.logback.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }
    testImplementation(libs.spring.mockk)
    testImplementation(libs.graphql.spring.boot.starter.test) {
        exclude(module = "mockito-core")
    }

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(libs.interop.commonTestDb)
    testImplementation(libs.interop.ehr.liquibase)
    testImplementation(libs.interop.queue.liquibase)
    testImplementation(libs.interop.testcontainer.aidbox)
    testImplementation(libs.interop.testcontainer.mockehr)

    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.jackson)
    testImplementation("com.squareup.okhttp3:mockwebserver")

    // Allows us to change environment variables
    testImplementation(libs.junit.pioneer)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.withType(Test::class) {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }

    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
}

// We need to exclude some dependencies from our it testSet
configurations.getByName("itImplementation") {
    exclude(module = "graphql-spring-boot-starter-test")
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
            name = "nexus"
            credentials {
                username = System.getenv("NEXUS_USER")
                password = System.getenv("NEXUS_TOKEN")
            }
            url = if (project.version.toString().endsWith("SNAPSHOT")) {
                uri("https://repo.devops.projectronin.io/repository/maven-snapshots/")
            } else {
                uri("https://repo.devops.projectronin.io/repository/maven-releases/")
            }
        }
    }
    publications {
        create<MavenPublication>("bootJava") {
            artifact(tasks.getByName("bootJar"))
        }
    }
}
