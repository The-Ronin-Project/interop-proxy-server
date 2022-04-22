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
    id("org.springframework.boot")
    id("com.expediagroup.graphql")
    id("com.google.cloud.tools.jib")
}

val tracerAgent: Configuration by configurations.creating

dependencies {
    implementation(libs.interop.aidbox)
    implementation(libs.interop.common)
    implementation(libs.interop.ehr.api)
    implementation(libs.interop.fhir)
    implementation(libs.interop.queue.api)
    implementation(libs.interop.tenant)
    implementation(libs.interop.transform)

    implementation(platform(libs.spring.boot.parent))
    // Pull in just the security dependencies we need, as we are not using the full security suite.
    implementation(libs.bundles.spring.security)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    implementation(libs.bundles.graphql)

    implementation(libs.mysql.connector.java)

    // Needed to format logs for DataDog
    implementation(libs.logstash.logback.encoder)

    // Dependency on the datadog agent jar.
    tracerAgent(libs.datadog.java.agent)

    // Runtime Dependency on each EHR implementation.
    runtimeOnly(libs.bundles.ehr.impls)
    runtimeOnly(libs.interop.queue.db)

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }
    testImplementation(libs.spring.mockk)
    testImplementation(libs.graphql.spring.boot.starter.test) {
        exclude(module = "mockito-core")
    }

    testImplementation(libs.interop.commonJackson)
    testImplementation(libs.interop.commonTestDb)
    testImplementation(libs.interop.ehr.liquibase)
    testImplementation(libs.interop.queue.liquibase)
    testImplementation(libs.interop.testcontainer.aidbox)
    testImplementation(libs.interop.testcontainer.mockehr)

    testImplementation("com.squareup.okhttp3:mockwebserver")

    // Allows us to change environment variables
    testImplementation(libs.junit.pioneer)
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
