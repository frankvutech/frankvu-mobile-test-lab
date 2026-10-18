plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plain Kotlin/JVM on purpose: the domain cannot compile against the Android SDK, which enforces
// the Clean Architecture dependency rule mechanically, and its tests run in milliseconds.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // Annotations only: use cases declare injection points without depending on Hilt.
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

tasks.withType<Test>().configureEach {
    useJUnit()
    testLogging { events("passed", "skipped", "failed") }
}
