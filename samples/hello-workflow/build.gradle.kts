plugins {
  id("com.android.application")
  kotlin("android")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.helloworkflow"
  }
}

dependencies {
  implementation(project(":workflow-ui:core-android"))

  implementation(libs.androidx.activityKtx)
  implementation(libs.androidx.lifecycle.viewModelKtx)
  implementation(libs.androidx.lifecycle.viewModelSavedState)
  implementation(libs.androidx.viewbinding)
}
