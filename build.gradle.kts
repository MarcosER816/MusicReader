// Root build file. Android modules use AGP's built-in Kotlin support; declaring the
// Kotlin Android plugin here (not applied) pins the Kotlin Gradle plugin version that
// AGP uses to the one in the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
