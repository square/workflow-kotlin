plugins {
  id("com.android.application")
  kotlin("android")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))
apply(from = rootProject.file(".buildscript/configure-kotlin-android-extensions.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.hellobackbutton"
  }
}

dependencies {
  implementation(project(":samples:containers:android"))
  implementation(project(":workflow-ui:core-android"))
}
