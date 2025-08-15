plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
  id("published")
}

android {
  namespace = "com.squareup.workflow1.tracing.papa"
}

dependencies {
  api(libs.androidx.collection)
  api(libs.kotlin.jdk8)
  api(libs.kotlinx.coroutines.core)
  api(libs.squareup.papa)

  api(project(":workflow-core"))
  api(project(":workflow-runtime"))
  api(project(":workflow-tracing"))

  testImplementation(project(":workflow-config:config-jvm"))
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.kotlinx.coroutines.test)
}
