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

  api(Dependencies.AndroidX.transition)
  api(Dependencies.Kotlin.Stdlib.jdk6)

  implementation(project(":samples:containers:android"))
  implementation(project(":workflow-runtime"))
  implementation(Dependencies.AndroidX.appcompat)
  implementation(Dependencies.AndroidX.recyclerview)
  implementation(Dependencies.AndroidX.savedstate)
  implementation(Dependencies.Kotlin.Coroutines.android)
  implementation(Dependencies.Kotlin.Coroutines.core)
  implementation(Dependencies.timber)

  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)
  testImplementation(Dependencies.Kotlin.Coroutines.test)
}
