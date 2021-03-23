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

dependencies {
  compileOnly(libs.androidx.viewbinding)

  api(project(":workflow-core"))
  // Needs to be API for the WorkflowInterceptor argument to WorkflowRunner.Config.
  api(project(":workflow-runtime"))
  api(project(":workflow-ui:core-common"))

  api(libs.androidx.transition)
  api(libs.kotlin.jdk6)

  implementation(libs.androidx.activity)
  implementation(libs.androidx.fragment)
  implementation(libs.androidx.lifecycle.ktx)
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
