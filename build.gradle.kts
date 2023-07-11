import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateSDLTask

plugins {
    java
    alias(libs.plugins.interop.spring.boot)
    alias(libs.plugins.interop.docker.integration)
    alias(libs.plugins.interop.version.catalog)
    alias(libs.plugins.graphql)
    alias(libs.plugins.dependencycheck)
}

dependencies {
    implementation(libs.kafka.clients)
    configurations.all {
        resolutionStrategy {
            force(libs.kafka.clients)
            force(libs.graphql.java)
        }
    }

    implementation(libs.ehrda.client)
    implementation(libs.ehrda.models)
    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.datalake) {
        // Exclude validation
        exclude(group = "com.projectronin.interop.validation", module = "interop-validation-client")
    }
    implementation(libs.interop.ehr.api)
    implementation(libs.interop.kafka)
    implementation(libs.interop.fhir)
    implementation(libs.interop.queue.api)
    implementation(libs.interop.ehr.tenant)
    implementation(libs.event.interop.resource.internal)
    implementation(libs.interop.ehr.fhir.ronin) {
        // Exclude validation
        exclude(group = "com.projectronin.interop.validation", module = "interop-validation-client")
    }
    implementation(libs.interop.commonJackson)

    implementation(libs.bundles.opentracing)

    implementation(platform(libs.kotlinx.coroutines.bom))

    implementation(platform(libs.spring.boot.parent)) {
        exclude(group = "org.jetbrains.kotlinx")
        // We don't use YAML config, and snakeyaml has some vulnerabilities.
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    // Pull in just the security dependencies we need, as we are not using the full security suite.
    implementation(libs.bundles.spring.security)
    implementation("org.springframework.boot:spring-boot-starter-jdbc") {
        // We don't use YAML config, and snakeyaml has some vulnerabilities.
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    implementation("org.springframework.boot:spring-boot-starter-actuator") {
        // We don't use YAML config, and snakeyaml has some vulnerabilities.
        exclude(group = "org.yaml", module = "snakeyaml")
    }

    implementation(libs.bundles.graphql) {
        // We don't use YAML config, and snakeyaml has some vulnerabilities.
        exclude(group = "org.yaml", module = "snakeyaml")
    }
    implementation(libs.bundles.hl7v2)
    implementation(libs.dd.trace.api)
    implementation(libs.ronin.kafka)

    runtimeOnly(libs.bundles.ehr.impls) {
        // Exclude validation
        exclude(group = "com.projectronin.interop.validation", module = "interop-validation-client")
    }
    runtimeOnly(libs.interop.queue.db)
    runtimeOnly(libs.interop.queue.kafka)
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

    testImplementation(libs.mockk)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(libs.interop.commonTestDb)
    testImplementation(libs.interop.ehr.liquibase)
    testImplementation(libs.interop.queue.liquibase)

    testImplementation("com.squareup.okhttp3:mockwebserver")

    // Allows us to change environment variables
    testImplementation(libs.junit.pioneer)

    itImplementation(project)
    itImplementation(libs.bundles.ktor)
    itImplementation(platform(libs.spring.boot.parent))
    itImplementation("com.squareup.okhttp3:mockwebserver")
    itImplementation("org.springframework:spring-web")
    itImplementation("org.springframework:spring-test")
    itImplementation("org.springframework.boot:spring-boot-starter-test")
    itImplementation("org.testcontainers:testcontainers")
    itImplementation("org.testcontainers:junit-jupiter")
    itImplementation(libs.spring.mockk)
    itImplementation(libs.kafka.clients)
    itImplementation(libs.ktorm.core)
    itImplementation(libs.ehrda.client)
    itImplementation(libs.ehrda.models)
    itImplementation(libs.interop.aidbox)
    itImplementation(libs.interop.common)
    itImplementation(libs.interop.commonHttp)
    itImplementation(libs.interop.ehr.liquibase)
    itImplementation(libs.interop.fhir)
    itImplementation(libs.interop.fhirGenerators)
    itImplementation(libs.interop.ehr.tenant)
    // itImplementation(libs.interop.ehr.fhir.ronin.generators)
    itImplementation(libs.ronin.test.data.generator)
    itImplementation("org.springframework.security:spring-security-oauth2-jose")
    itImplementation("org.liquibase:liquibase-core")
    itImplementation(libs.kotlin.logging)
    itImplementation(libs.jackson.databind)
}

tasks.withType(Test::class) {
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

val graphqlGenerateSDL by tasks.getting(GraphQLGenerateSDLTask::class) {
    packages.set(listOf("com.projectronin.interop.proxy.server"))
    schemaFile.set(file("${project.projectDir}/interopSchema.graphql"))
}

// We want to tie the GraphQL schema generation to the kotlin compile step.
tasks.compileKotlin.get().finalizedBy(graphqlGenerateSDL)

dependencyCheck {
    scanConfigurations = listOf("compileClasspath", "runtimeClasspath")
    skipTestGroups = true
    suppressionFile = "conf/owasp-suppress.xml"
}
