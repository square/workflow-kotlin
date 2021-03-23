plugins {
  id("com.android.library")
  kotlin("android")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))

dependencies {
  api(project(":workflow-core"))
  api(project(":workflow-ui:backstack-android"))
  api(project(":samples:containers:common"))

  api(libs.androidx.transition)

  implementation(project(":samples:containers:android"))
  implementation(project(":workflow-runtime"))
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlin.coroutines.android)
  implementation(libs.kotlin.coroutines.core)
  implementation(libs.timber)

  testImplementation(libs.test.junit)
  testImplementation(libs.test.truth)
  testImplementation(libs.test.coroutines)
}
