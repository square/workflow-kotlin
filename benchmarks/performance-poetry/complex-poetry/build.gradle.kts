plugins {
  id("com.android.application")
  kotlin("android")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.benchmarks.performance.complex.poetry"
  }
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(project(":samples:containers:android"))
  implementation(project(":samples:containers:poetry"))
  implementation(project(":workflow-ui:core-android"))

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.recyclerview)
}
