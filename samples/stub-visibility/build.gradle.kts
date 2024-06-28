plugins {
  id("com.android.application")
  id("kotlin-android")
  id("android-sample-app")
  id("android-ui-tests")
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.stubvisibility"
  }
  namespace = "com.squareup.sample.stubvisibility"
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.core)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.viewbinding)

  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core"))
}
