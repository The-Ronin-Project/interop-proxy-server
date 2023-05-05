import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateSDLTask

plugins {
    java
    id("com.projectronin.interop.gradle.spring-boot")
    id("com.projectronin.interop.gradle.integration")
    id("com.expediagroup.graphql")
    id("org.owasp.dependencycheck")
}

dependencies {
    implementation(libs.kafka.clients)
    configurations.all {
        resolutionStrategy {
            force(libs.kafka.clients)
        }
    }

    implementation(libs.interop.publishers.aidbox)
    implementation(libs.interop.common)
    implementation(libs.interop.commonHttp)
    implementation(libs.interop.publishers.datalake) {
        // Exclude validation
        exclude(group = "com.projectronin.interop.validation", module = "interop-validation-client")
    }
    implementation(libs.interop.ehr.api)
    implementation(libs.interop.publishers.kafka)
    implementation(libs.interop.fhir)
    implementation(libs.interop.queue.api)
    implementation(libs.interop.ehr.tenant)
    implementation(libs.interop.ehr.fhir.ronin) {
        // Exclude validation
        exclude(group = "com.projectronin.interop.validation", module = "interop-validation-client")
    }
    implementation(libs.interop.commonJackson)

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
    testImplementation(libs.interop.testcontainer.aidbox)
    testImplementation(libs.interop.testcontainer.mockehr)

    testImplementation("com.squareup.okhttp3:mockwebserver")

    // Allows us to change environment variables
    testImplementation(libs.junit.pioneer)

    itImplementation(platform(libs.spring.boot.parent))
    itImplementation("com.squareup.okhttp3:mockwebserver")
    itImplementation("org.springframework:spring-web")
    itImplementation("org.springframework:spring-test")
    itImplementation("org.springframework.boot:spring-boot-starter-test")
    itImplementation("org.testcontainers:testcontainers")
    itImplementation("org.testcontainers:junit-jupiter")
    itImplementation(libs.interop.testcontainer.aidbox)
    itImplementation(libs.interop.testcontainer.mockehr)
    itImplementation(libs.spring.mockk)
    itImplementation(libs.kafka.clients)
    itImplementation(libs.interop.publishers.aidbox)
    itImplementation(libs.interop.common)
    itImplementation(libs.interop.commonHttp)
    itImplementation(libs.interop.queue.liquibase)
    itImplementation(libs.interop.ehr.liquibase)
    itImplementation("org.springframework.security:spring-security-oauth2-jose")
    itImplementation("org.liquibase:liquibase-core")
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
