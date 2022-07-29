plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
}

dependencies {
  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  api(project(":samples:containers:common"))
  api(project(":workflow-core"))
  api(project(":workflow-ui:container-android"))

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.timber)

  implementation(project(":samples:containers:android"))
  implementation(project(":workflow-runtime"))

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.truth)
}
