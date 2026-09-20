// AGP 9 bringt Kotlin selbst mit (android.builtInKotlin, Standard seit 9.0).
// Der explizite Classpath-Eintrag hebt die mitgelieferte Kotlin-Version auf die
// im Versionskatalog festgelegte an; das Plugin org.jetbrains.kotlin.android
// wird deshalb bewusst nicht angewendet.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.hilt) apply false
}
