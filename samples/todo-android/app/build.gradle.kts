plugins {
  id("com.android.application")
  kotlin("android")
  id("app.cash.molecule")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.todo"
    multiDexEnabled = true
  }
}

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
  debugImplementation(Dependencies.leakcanary)

  implementation(project(":samples:containers:android"))
  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-core-compose"))
  implementation(project(":workflow-rx2"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:compose"))
  implementation(project(":workflow-ui:container-common"))
  implementation(project(":workflow-tracing"))

  implementation(Dependencies.AndroidX.activityKtx)
  implementation(Dependencies.AndroidX.constraint_layout)
  implementation(Dependencies.AndroidX.material)
  implementation(Dependencies.Kotlin.Coroutines.rx2)
  implementation(Dependencies.okio)
  implementation(Dependencies.rxandroid2)
  implementation(Dependencies.timber)

  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)

  androidTestImplementation(Dependencies.Test.AndroidX.uiautomator)
}
