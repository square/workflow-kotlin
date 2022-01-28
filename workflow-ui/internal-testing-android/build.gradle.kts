plugins {
  id("com.android.library")
  kotlin("android")
  id("org.jetbrains.dokka")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

// This module is not published, since it's just internal testing utilities.
apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))

dependencies {
  api(project(":workflow-ui:core-android"))

  api(Dependencies.AndroidX.appcompat)
  api(Dependencies.Kotlin.Stdlib.jdk6)
  api(Dependencies.Test.AndroidX.Espresso.core)

  implementation(Dependencies.Test.leakcanaryInstrumentation)
}
