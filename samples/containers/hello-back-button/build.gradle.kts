plugins {
  id("com.android.application")
  kotlin("android")
  id("kotlin-parcelize")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.hellobackbutton"
  }
}

dependencies {
  debugImplementation(Dependencies.leakcanary)

  implementation(project(":samples:containers:android"))
  implementation(project(":workflow-ui:core-android"))
  implementation(Dependencies.AndroidX.activityKtx)
}
