plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
}

android {
  namespace = "com.squareup.sample.container.poetry"
}

dependencies {
  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  api(project(":samples:containers:common"))
  api(project(":workflow-core"))
  api(project(":workflow-ui:core-android"))
  api(project(":workflow-ui:core"))

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.timber)

  implementation(project(":samples:containers:android"))

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.truth)
}
