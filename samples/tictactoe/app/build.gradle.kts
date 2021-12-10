plugins {
  id("com.android.application")
  kotlin("android")
  id("app.cash.molecule")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf(
      "-P", "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true",
      "-Xopt-in=kotlin.RequiresOptIn"
    )
  }
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.tictacworkflow"
    multiDexEnabled = true
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  debugImplementation(Dependencies.leakcanary)

  implementation(project(":samples:containers:android"))
  implementation(project(":samples:tictactoe:common"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-tracing"))

  implementation(Dependencies.AndroidX.activityKtx)
  implementation(Dependencies.AndroidX.constraint_layout)
  implementation(Dependencies.AndroidX.Lifecycle.ktx)
  implementation(Dependencies.okio)
  implementation(Dependencies.rxandroid2)
  implementation(Dependencies.Test.AndroidX.Espresso.idlingResource)
  implementation(Dependencies.timber)

  androidTestImplementation(Dependencies.Test.AndroidX.Espresso.intents)
  androidTestImplementation(Dependencies.Test.AndroidX.runner)
  androidTestImplementation(Dependencies.Test.AndroidX.truthExt)
  androidTestImplementation(Dependencies.Test.junit)
  androidTestImplementation(Dependencies.Test.truth)
}
