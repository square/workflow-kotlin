plugins {
  id("com.android.application")
  kotlin("android")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.stubvisibility"
  }
}

dependencies {
  implementation(project(":workflow-ui:core-android"))

  implementation(Dependencies.AndroidX.activityKtx)
  implementation(Dependencies.AndroidX.viewbinding)
}
