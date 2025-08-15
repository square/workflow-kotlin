plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
  id("published")
}

android {
  namespace = "com.squareup.workflow1.tracing"
}

dependencies {
  api(libs.androidx.collection)
  api(libs.kotlin.jdk8)
  api(libs.kotlinx.coroutines.core)

  api(project(":workflow-core"))
  api(project(":workflow-runtime"))

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.kotlinx.coroutines.test)
}
