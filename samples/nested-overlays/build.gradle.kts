plugins {
  id("com.android.application")
  id("kotlin-android")
  id("android-sample-app")
  id("android-ui-tests")
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.nestedoverlays"
  }
  namespace = "com.squareup.sample.nestedoverlays"
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.viewbinding)

  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core"))
}
