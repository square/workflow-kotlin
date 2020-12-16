plugins {
  id("com.android.application")
  kotlin("android")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.helloworkflowfragment"
  }
}

dependencies {
  implementation(project(":workflow-ui:core-android"))

  implementation(Dependencies.AndroidX.fragmentKtx)
  implementation(Dependencies.AndroidX.Lifecycle.viewModelKtx)
  implementation(Dependencies.AndroidX.Lifecycle.viewModelSavedState)
}
