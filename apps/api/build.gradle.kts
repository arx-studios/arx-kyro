plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.openapi.generator)
}

group = "com.kyro"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val springBom = org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES

dependencies {
    implementation(platform(springBom))
    implementation(platform(libs.awssdk.bom))
    annotationProcessor(platform(springBom))
    testImplementation(platform(springBom))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation(libs.awssdk.s3)

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ---- Contract-first codegen (PLAN section 3) ----
val openApiOutput = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set(rootProject.file("packages/api-spec/openapi.yaml").absolutePath)
    outputDir.set(openApiOutput.map { it.asFile.absolutePath })
    apiPackage.set("com.kyro.platform.contract.api")
    modelPackage.set("com.kyro.platform.contract.model")
    invokerPackage.set("com.kyro.platform.contract.invoker")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useTags" to "true",
            "useSpringBoot3" to "true",
            "useJakartaEe" to "true",
            "useBeanValidation" to "true",
            "openApiNullable" to "false",
            "skipDefaultInterface" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "dateLibrary" to "java8",
        ),
    )
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

// The spring generator still emits one Jackson 2 databind annotation (@JsonDeserialize on Set properties), but
// Boot 4 uses Jackson 3 (tools.jackson.*) where it does not exist. Jackson 3 builds a Set without it, so strip it.
tasks.named("openApiGenerate") {
    doLast {
        fileTree(openApiOutput) { include("**/*.java") }.forEach { f ->
            val text = f.readText()
            if (text.contains("com.fasterxml.jackson.databind")) {
                f.writeText(
                    text.replace(Regex("import com\\.fasterxml\\.jackson\\.databind[^;]*;\\R"), "")
                        .replace(Regex("[ \\t]*@JsonDeserialize\\([^)]*\\)\\R"), ""),
                )
            }
        }
    }
}

sourceSets {
    main {
        java.srcDir(openApiOutput.map { it.dir("src/main/java") })
    }
}

tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.profiles.active", System.getenv("SPRING_PROFILES_ACTIVE") ?: "local")
}
