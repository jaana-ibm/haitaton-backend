import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "fi.hel.haitaton"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11
val postgreSQLVersion = "42.2.18"
val springDocVersion = "1.4.8"
val geoJsonJacksonVersion = "1.14"
val mockkVersion = "1.10.2"
val springmockkVersion = "2.0.3"
val assertkVersion = "0.23"

repositories {
    mavenCentral()
    jcenter()
}

sourceSets {
    create("integrationTest") {
        compileClasspath += main.get().output + test.get().output
        runtimeClasspath += main.get().output + test.get().output
    }
    create("manualTest") {
        compileClasspath += main.get().output + test.get().output
        runtimeClasspath += main.get().output + test.get().output
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val manualTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())
configurations["manualTestRuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())

plugins {
    id("org.springframework.boot") version "2.3.4.RELEASE"
    id("io.spring.dependency-management") version "1.0.10.RELEASE"
    kotlin("jvm") version "1.4.20"
    // Gives kotlin-allopen, which auto-opens classes with certain annotations
    kotlin("plugin.spring") version "1.4.20"
    // Gives kotlin-noarg for @Entity, @Embeddable
    kotlin("plugin.jpa") version "1.4.20"
    idea
    pmd
    id("io.gitlab.arturbosch.detekt") version "1.16.0-RC1"
    jacoco
    id("org.sonarqube") version "3.1.1"
    id("org.owasp.dependencycheck") version "6.1.1"
}

idea {
    module {
        testSourceDirs =
            testSourceDirs + sourceSets["integrationTest"].withConvention(KotlinSourceSet::class) { kotlin.srcDirs }
        testResourceDirs = testResourceDirs + sourceSets["integrationTest"].resources.srcDirs
        testSourceDirs =
            testSourceDirs + sourceSets["manualTest"].withConvention(KotlinSourceSet::class) { kotlin.srcDirs }
        testResourceDirs = testResourceDirs + sourceSets["manualTest"].resources.srcDirs
    }
}

springBoot {
    buildInfo()
}

pmd {
    isConsoleOutput = true
}

detekt {
    // config = files("detekt.yml")
    ignoreFailures = true
}

jacoco {
    tasks.jacocoTestReport {
        executionData(fileTree(projectDir).setIncludes(listOf("**/*.exec")))
        reports {
            xml.isEnabled = true
            html.isEnabled = true
            csv.isEnabled = false
        }
    }
}

sonarqube {
    properties {
        property("sonar.projectKey", "HAI")
        property("sonar.organization", "umeetiusbaarus")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.kotlin.detekt.reportPaths", "build/reports/detekt/detekt.xml")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/**/jacocoTestReport.xml")
    }
}

dependencyCheck {
    format = org.owasp.dependencycheck.reporting.ReportGenerator.Format.ALL
    outputDirectory = "build/reports/dependency-check"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.microutils:kotlin-logging:1.12.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("de.grundid.opendatalab:geojson-jackson:$geoJsonJacksonVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.liquibase:liquibase-core")

    implementation("org.postgresql:postgresql:$postgreSQLVersion")
    // H2 is used as embedded db for some simple low level Entity and Repository class testing
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.springdoc:springdoc-openapi-ui:$springDocVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.ninja-squad:springmockk:$springmockkVersion")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")
    testImplementation("org.testcontainers:junit-jupiter:1.15.1")
    testImplementation("org.testcontainers:postgresql:1.15.1")
    // Spring Boot Management
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.cloud:spring-cloud-starter-oauth2:2.2.4.RELEASE")
    implementation("org.springframework.security:spring-security-data")
    implementation("org.springframework.security:spring-security-oauth2-resource-server:5.3.6.RELEASE")
    implementation("org.springframework.security:spring-security-oauth2-jose:5.3.6.RELEASE")
    testImplementation("org.springframework.security:spring-security-test")
    // Sentry
    implementation("io.sentry:sentry-spring-boot-starter:4.0.0")
    implementation("io.sentry:sentry-logback:4.0.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("spring.profiles.active", "test")
    }

    create("integrationTest", Test::class) {
        useJUnitPlatform()
        group = "verification"
        systemProperty("spring.profiles.active", "integrationTest")
        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        shouldRunAfter("test")
        outputs.upToDateWhen { false }
    }

    create("manualTest", Test::class) {
        useJUnitPlatform()
        group = "verification"
        systemProperty("spring.profiles.active", "manualTest")
        testClassesDirs = sourceSets["manualTest"].output.classesDirs
        classpath = sourceSets["manualTest"].runtimeClasspath
        outputs.upToDateWhen { false }
        exclude("**/*ManualTest")
    }
}
