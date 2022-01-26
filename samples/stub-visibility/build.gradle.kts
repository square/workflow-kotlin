plugins {
  id("com.android.application")
  kotlin("android")
  id("android-sample-app")
  id("android-ui-tests")
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.stubvisibility"
  }
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(project(":workflow-ui:core-android"))

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.viewbinding)
}
