plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
  id("android-ui-tests")
  id("app.cash.burst")
}

android {
  namespace = "com.squareup.workflow1.android"
  testNamespace = "$namespace.test"
}

dependencies {
  api(project(":workflow-runtime"))
  implementation(project(":workflow-core"))

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.kotlin.test.core)
  androidTestImplementation(libs.kotlin.test.jdk)
  androidTestImplementation(libs.kotlinx.coroutines.android)
  androidTestImplementation(libs.kotlinx.coroutines.core)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.squareup.papa)
  androidTestImplementation(libs.turbine)
}
