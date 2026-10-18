plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Code quality is configured once here and applied to every module, so module build files only
// declare their own dependencies.
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.engine.get())
        android.set(true)
        ignoreFailures.set(false)
        filter {
            exclude { entry -> entry.file.path.contains("generated") }
        }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(true)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
}

// Single entry point used by the Makefile, Docker and CI, so every environment runs the same checks.
tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs ktlint, detekt, Android Lint and every JVM unit test."
    dependsOn(
        ":core:domain:ktlintCheck",
        ":core:data:ktlintCheck",
        ":core:ui:ktlintCheck",
        ":app:ktlintCheck",
        ":core:domain:detekt",
        ":core:data:detekt",
        ":core:ui:detekt",
        ":app:detekt",
        ":app:lintDebug",
        ":core:domain:test",
        ":core:data:testDebugUnitTest",
        ":app:testDebugUnitTest",
    )
}
