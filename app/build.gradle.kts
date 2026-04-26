plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.capsule.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.capsule.app"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Export Room schemas for migration testing
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    lint {
        abortOnError = true
    }
}

dependencies {
    // Custom lint rules — Principle VI enforcement
    lintChecks(project(":build-logic:lint"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Room + SQLCipher
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)

    // AppFunctions (003 v1.1) — agent-callable function registry. KSP processor
    // generates argsSchemaJson constants from @AppFunction-annotated args data
    // classes (see com.capsule.app.action.AppFunctionAnnotations).
    implementation(libs.androidx.appfunctions.runtime)
    ksp(libs.androidx.appfunctions.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Security (Keystore helpers)
    implementation(libs.androidx.security.crypto)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Jsoup (Readability extraction, :net process only)
    implementation(libs.jsoup)

    // OkHttp + Readability4J (:net process only — enforced by OrbitNoHttpClientOutsideNet lint rule)
    implementation(libs.okhttp)
    implementation(libs.readability4j)

    // ML Kit text recognition — T075 OcrEngine (on-device, Latin script bundled model)
    implementation(libs.mlkit.text.recognition)

    // Coil Compose — T078 screenshot thumbnails in EnvelopeCard
    implementation(libs.coil.compose)

    // Google Play Services Location — T081 ActivityRecognitionClient wrapper
    implementation(libs.play.services.location)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android's org.json is stubbed in mockable android.jar (returns null/0);
    // bring in the real impl so VM tests that parse JSON (e.g. intentHistoryJson
    // in EnvelopeDetailViewModel) can round-trip in JVM tests.
    testImplementation("org.json:json:20240303")

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.okhttp.mockwebserver)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}