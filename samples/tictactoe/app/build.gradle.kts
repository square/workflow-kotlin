plugins {
  id("com.android.application")
  kotlin("android")
}

apply(from = rootProject.file(".buildscript/android-sample-app.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  defaultConfig {
    applicationId = "com.squareup.sample.tictacworkflow"
    multiDexEnabled = true
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":samples:containers:android"))
  implementation(project(":samples:tictactoe:common"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-tracing"))

  implementation(libs.androidx.activityKtx)
  implementation(libs.androidx.constraint)
  implementation(libs.androidx.lifecycle.ktx)
  implementation(libs.okio)
  implementation(libs.rxandroid2)
  implementation(libs.test.espresso.idlingResource)
  implementation(libs.timber)

  androidTestImplementation(libs.test.espresso.intents)
  androidTestImplementation(libs.test.androidx.runner)
  androidTestImplementation(libs.test.androidx.truthExt)
  androidTestImplementation(libs.test.junit)
  androidTestImplementation(libs.test.truth)
}
