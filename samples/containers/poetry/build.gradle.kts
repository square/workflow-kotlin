plugins {
  id("com.android.library")
  `kotlin-android`
  `android-defaults`
}

dependencies {
  api(project(":workflow-core"))
  api(project(":workflow-ui:container-android"))
  api(project(":samples:containers:common"))

  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  implementation(project(":samples:containers:android"))
  implementation(project(":workflow-runtime"))
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.timber)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
