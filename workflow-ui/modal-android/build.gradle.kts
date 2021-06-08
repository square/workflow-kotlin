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

  api(Dependencies.AndroidX.transition)
  api(Dependencies.Kotlin.Stdlib.jdk6)

  implementation(project(":workflow-runtime"))
  implementation(Dependencies.AndroidX.appcompat)
  implementation(Dependencies.AndroidX.activity)
  implementation(Dependencies.AndroidX.fragment)
  implementation(Dependencies.AndroidX.savedstate)
  implementation(Dependencies.Kotlin.Coroutines.android)
  implementation(Dependencies.Kotlin.Coroutines.core)

  testImplementation(Dependencies.Kotlin.Coroutines.test)
  testImplementation(Dependencies.Kotlin.Test.jdk)
  testImplementation(Dependencies.Kotlin.Test.mockito)
  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)
  testImplementation(Dependencies.Test.AndroidX.core)
  testImplementation(Dependencies.Test.robolectric)

  androidTestImplementation(Dependencies.Test.truth)
}
