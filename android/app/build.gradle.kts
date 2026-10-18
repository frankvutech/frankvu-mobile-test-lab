plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Test Mode (state reset, storage wipe, auth bypass) is on in debug builds and off in release.
// `-PenableTestMode=true` turns it on for a release build, e.g. for a release-candidate test run.
val testModeEnabledForRelease: Boolean =
    (project.findProperty("enableTestMode") as String?)?.toBoolean() ?: false

// The backend API. 10.0.2.2 is the Android emulator's address for the host machine; a physical
// device needs the host's LAN address: `./gradlew installDebug -PapiBaseUrl=http://192.168.1.20:8080/`
// (or set it at runtime in Test Mode). `-PuseBackend=false` builds with the offline/test mode as default.
val apiBaseUrl: String = (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:8080/"
val useBackendByDefault: Boolean = (project.findProperty("useBackend") as String?)?.toBoolean() ?: true

android {
    namespace = "com.mobiletestplayground.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mobiletestplayground.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("boolean", "USE_BACKEND_BY_DEFAULT", useBackendByDefault.toString())

        testInstrumentationRunner = "com.mobiletestplayground.app.HiltTestRunner"
        // Wipes app data between every instrumented test. Only effective with the orchestrator below.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    // The WebView page is shared with iOS, so it lives outside the Android tree.
    sourceSets["main"].assets.srcDir(rootProject.file("../shared/web"))

    androidResources {
        localeFilters += listOf("en", "de")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_TEST_MODE", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "ENABLE_TEST_MODE", testModeEnabledForRelease.toString())
            // Debug signing so `assembleRelease` yields an installable APK for practice runs.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }

    testOptions {
        // One process per test so clearPackageData gives every test a fresh install.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        disable += setOf(
            // Only en and de are in scope.
            "MissingTranslation",
            // compileSdk/targetSdk and dependency versions are pinned deliberately (see libs.versions.toml).
            "OldTargetApi",
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "NewerVersionAvailable",
        )
        xmlReport = true
        htmlReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.ui)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestUtil(libs.androidx.test.orchestrator)
    kspAndroidTest(libs.hilt.compiler)
}
