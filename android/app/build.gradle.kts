/**
 * Signaturdaten kommen ausschließlich aus Gradle-Properties, die außerhalb
 * dieses Repositories liegen (~/.gradle/gradle.properties). Fehlen sie -
 * etwa auf einem fremden Rechner oder in CI ohne Secrets -, entsteht ein
 * unsigniertes Release statt eines Build-Fehlers.
 */
val signingProperties = listOf(
    "COMMENTATOR_STORE_FILE",
    "COMMENTATOR_STORE_PASSWORD",
    "COMMENTATOR_KEY_ALIAS",
    "COMMENTATOR_KEY_PASSWORD",
).associateWith { providers.gradleProperty(it).orNull }

val canSignRelease = signingProperties.values.none { it.isNullOrBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
}

android {
    namespace = "de.christophlangner.commentator"
    compileSdk = 37

    // Explizit gesetzt: AGP 9.4 würde sonst 36.0.0 anfordern und versuchen,
    // sie nachzuinstallieren. Installiert ist 37.0.0.
    buildToolsVersion = "37.0.0"

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(signingProperties.getValue("COMMENTATOR_STORE_FILE")!!)
                storePassword = signingProperties.getValue("COMMENTATOR_STORE_PASSWORD")
                keyAlias = signingProperties.getValue("COMMENTATOR_KEY_ALIAS")
                keyPassword = signingProperties.getValue("COMMENTATOR_KEY_PASSWORD")

                // v1 ist für minSdk 26 überflüssig; v2 und v3 decken alles ab,
                // was die App unterstützt.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "de.christophlangner.commentator"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "de.christophlangner.commentator.HiltTestRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
            )
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        // Bewusst aus: Mit checkDependencies analysiert Lint sämtliche
        // Bibliotheken mit und verdreifacht die Laufzeit, ohne für diese App
        // je einen eigenen Befund geliefert zu haben.
        checkDependencies = false
    }
}

room {
    // Das exportierte Schema gehört ins Repository: Nur damit lassen sich
    // spätere Migrationen automatisiert prüfen.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
}
