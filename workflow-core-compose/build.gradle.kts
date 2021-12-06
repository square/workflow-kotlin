plugins {
  id("com.android.library")
  kotlin("android")
  id("app.cash.molecule")
}

apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf(
        "-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true",
        "-Xopt-in=kotlin.RequiresOptIn"
    )
  }
}

dependencies {
  api(project(":workflow-core"))
  api(Dependencies.Kotlin.Coroutines.core)

  testImplementation(Dependencies.Kotlin.Coroutines.test)
}
