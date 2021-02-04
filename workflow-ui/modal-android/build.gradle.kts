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

  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)
  testImplementation(Dependencies.Kotlin.Coroutines.test)
  testImplementation(Dependencies.Kotlin.Test.jdk)
  testImplementation(Dependencies.Kotlin.Test.mockito)

  androidTestImplementation(Dependencies.Test.truth)
}
