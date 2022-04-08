import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateSDLTask

plugins {
    `java`
    `maven-publish`
    id("com.projectronin.interop.gradle.mockk")
    id("com.projectronin.interop.gradle.ktorm")
    id("com.projectronin.interop.gradle.ktor")
    id("com.projectronin.interop.gradle.spring")
    id("com.projectronin.interop.gradle.jackson")
    id("com.projectronin.interop.gradle.integration")
    id("org.springframework.boot") version "2.4.5"
    id("com.expediagroup.graphql") version "4.2.0"
    id("com.google.cloud.tools.jib") version "3.2.0"
}

val tracerAgent: Configuration by configurations.creating

dependencies {
    implementation("com.projectronin.interop:interop-common:${project.property("interopCommonVersion")}")
    implementation("com.projectronin.interop.ehr:interop-ehr:${project.property("interopEhrVersion")}")
    implementation("com.projectronin.interop.ehr:interop-tenant:${project.property("interopEhrVersion")}")
    implementation("com.projectronin.interop.ehr:interop-transform:${project.property("interopEhrVersion")}")
    implementation("com.projectronin.interop.fhir:interop-fhir:${project.property("interopFhirVersion")}")
    implementation("com.projectronin.interop.publish:interop-aidbox:${project.property("interopPublishVersion")}")
    implementation("com.projectronin.interop.queue:interop-queue:${project.property("interopQueueVersion")}")
    implementation("com.projectronin.interop.queue:interop-queue-db:${project.property("interopQueueVersion")}")

    implementation(platform("org.springframework.boot:spring-boot-parent:2.6.4"))
    // Pull in just the security dependencies we need, as we are not using the full security suite.
    implementation("org.springframework.security:spring-security-oauth2-core")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("mysql:mysql-connector-java:8.0.25")

    implementation("com.expediagroup:graphql-kotlin-schema-generator:5.3.2")
    implementation("com.expediagroup:graphql-kotlin-spring-server:5.3.2")

    // Dependency on the datadog agent jar.
    tracerAgent("com.datadoghq:dd-java-agent:0.94.1")

    // Runtime Dependency on each EHR implementation.
    runtimeOnly("com.projectronin.interop.ehr:interop-ehr-epic:${project.property("interopEhrVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }
    testImplementation("com.ninja-squad:springmockk:3.1.0")
    testImplementation("com.graphql-java-kickstart:graphql-spring-boot-starter-test:12.0.0") {
        exclude(module = "mockito-core")
    }
    testImplementation("com.projectronin.interop.mock:interop-mock-ehr-testcontainer:${project.property("interopMockVersion")}")
    testImplementation("com.projectronin.interop.publish:interop-aidbox-testcontainer:${project.property("interopPublishVersion")}")
    testImplementation("com.projectronin.interop:interop-common-test-db:${project.property("interopCommonVersion")}")
    testImplementation("com.projectronin.interop:interop-common-jackson:${project.property("interopCommonVersion")}")
    testImplementation("com.projectronin.interop.ehr:interop-ehr-liquibase:${project.property("interopEhrVersion")}")
    testImplementation("com.projectronin.interop.queue:interop-queue-liquibase:${project.property("interopQueueVersion")}")

    testImplementation("com.projectronin.interop.ehr:interop-ehr-epic:${project.property("interopEhrVersion")}")

    testImplementation("com.squareup.okhttp3:mockwebserver")

    // Allows us to change environment variables
    testImplementation("org.junit-pioneer:junit-pioneer:1.5.0")

    itImplementation("com.projectronin.interop:interop-common-jackson:${project.property("interopCommonVersion")}")
}

tasks.withType(Test::class) {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
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

val copyTraceAgent by tasks.register<Copy>("copyTraceAgent") {
    // Copy the tracer agent jar from the repo to a project folder.
    from(tracerAgent.asPath)
    into("$buildDir/tracer")
}

// Jib depends on getting the trace agent.
tasks.jib.get().dependsOn(copyTraceAgent)
tasks.jibDockerBuild.get().dependsOn(copyTraceAgent)
tasks.jibBuildTar.get().dependsOn(copyTraceAgent)

jib {
    to {
        image = "interop-proxy-server"
    }
    extraDirectories {
        paths {
            path {
                // Pull in the tracer jar to the container image.
                // https://github.com/GoogleContainerTools/jib/issues/2715
                setFrom("$buildDir/tracer")
                into = "/opt/tracer"
            }
        }
    }
    container {
        jvmFlags = listOf("-javaagent:/opt/tracer/${tracerAgent.files.first().name}")
        environment = mapOf(
            "DD_SERVICE" to project.name,
            "DD_VERSION" to "$project.version",
            "DD_APM_ENABLED" to "false",
            // automatically include dd.correlation_id and dd.span_id in log MDC
            "DD_LOGS_INJECTION" to "true"
        )
    }
}
