plugins {
  id("com.android.application")
  kotlin("android")
  id("kotlin-parcelize")
  `android-sample-app`
  `android-ui-tests`
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.hellobackbutton"
  }
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(project(":samples:containers:android"))
  implementation(project(":workflow-ui:core-android"))
  implementation(libs.androidx.activity.ktx)
}
