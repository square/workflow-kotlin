plugins {
  id("com.android.library")
  kotlin("android")
  id("org.jetbrains.dokka")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))

android {
  testOptions.animationsDisabled = true
}

dependencies {
  api(project(":workflow-core"))
  api(project(":workflow-ui:core-android"))
  api(project(":workflow-ui:modal-common"))

  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  implementation(project(":workflow-runtime"))
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity)
  implementation(libs.androidx.fragment)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlin.coroutines.android)
  implementation(libs.kotlin.coroutines.core)

  testImplementation(libs.test.junit)
  testImplementation(libs.test.truth)
  testImplementation(libs.test.coroutines)
  testImplementation(libs.test.kotlin.jdk)
  testImplementation(libs.test.kotlin.mockito)

  androidTestImplementation(libs.test.truth)
}
