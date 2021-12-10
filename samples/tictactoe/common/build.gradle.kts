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
  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-ui:container-common"))
  implementation(project(":workflow-core"))
  api(project(":workflow-core-compose"))
  implementation(project(":workflow-rx2"))

  implementation(Dependencies.AndroidX.Compose.rx2)

  implementation(Dependencies.Kotlin.Coroutines.rx2)
  implementation(Dependencies.Kotlin.Stdlib.jdk6)

  testImplementation(Dependencies.Test.hamcrestCore)
  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)
  testImplementation(project(":workflow-testing"))
}
