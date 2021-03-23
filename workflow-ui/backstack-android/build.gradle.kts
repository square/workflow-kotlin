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
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  // See https://github.com/Kotlin/kotlinx.coroutines/issues/1064#issuecomment-479412940
  packagingOptions.exclude("**/*.kotlin_*")
}

dependencies {
  api(project(":workflow-core"))
  api(project(":workflow-ui:backstack-common"))
  api(project(":workflow-ui:core-android"))

  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  implementation(project(":workflow-runtime"))
  implementation(libs.androidx.activity)
  implementation(libs.androidx.fragment)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlin.coroutines.android)
  implementation(libs.kotlin.coroutines.core)

  androidTestImplementation(libs.test.androidx.core)
  androidTestImplementation(libs.test.androidx.truthExt)
}
