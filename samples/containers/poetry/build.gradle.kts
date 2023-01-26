plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
}

android {
  namespace = "com.squareup.sample.container.poetry"
}

dependencies {
  api(libs.androidx.transition)
  api(libs.kotlin.stdlib)

  api(project(":samples:containers:common"))
  api(project(":workflow-core"))
  api(project(":workflow-ui:core-android"))
  api(project(":workflow-ui:core-common"))

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
