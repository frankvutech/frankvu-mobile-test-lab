plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("io.ktor.plugin") version "3.2.3"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.mobiletestplayground"
version = "1.0.0"

application {
    mainClass.set("com.mobiletestplayground.backend.ApplicationKt")
}

// Compiles to Java 17 bytecode with whatever JDK (17+) runs Gradle; no toolchain download.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val exposedVersion = "0.61.0"

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-call-id")
    implementation("io.ktor:ktor-server-swagger")
    implementation("io.ktor:ktor-server-cors")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation(kotlin("test-junit"))
}

ktlint {
    version.set("1.7.1")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("config/detekt.yml"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}

tasks.test {
    testLogging { events("passed", "skipped", "failed") }
}

ktor {
    fatJar {
        archiveFileName.set("backend.jar")
    }
}

// H2 and PostgreSQL both register a JDBC driver through META-INF/services/java.sql.Driver; without
// merging, the fat JAR keeps only one of them and the other database URL fails with "No suitable driver".
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    mergeServiceFiles()
}
