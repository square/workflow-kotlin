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
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(project(":samples:containers:android"))
  implementation(project(":samples:tictactoe:common"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-tracing"))

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.lifecycle.ktx)
  implementation(libs.squareup.okio)
  implementation(libs.rxjava2.rxandroid)
  implementation(libs.androidx.test.espresso.idlingResource)
  implementation(libs.timber)

  androidTestImplementation(libs.androidx.test.espresso.intents)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.truth)
}
