plugins {
  id("com.android.application")
  id("kotlin-android")
  id("android-sample-app")
  id("android-ui-tests")
  alias(libs.plugins.compose.compiler)
}

android {
  defaultConfig {
    applicationId = "com.squareup.sample.hellocomposeworkflow"
  }
  namespace = "com.squareup.sample.hellocomposeworkflow"
}

dependencies {
  debugImplementation(libs.squareup.leakcanary.android)

  implementation(libs.androidx.activity.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.viewbinding)

  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core-common"))

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(project(":workflow-testing"))
}
