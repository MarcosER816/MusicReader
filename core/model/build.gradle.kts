// Pure Kotlin/JVM module: no Android dependencies.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

// CI runs `./gradlew testDebugUnitTest`; make that task run this module's JVM tests too.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the unit tests of this pure Kotlin module (alias of test)."
    dependsOn(tasks.named("test"))
}
