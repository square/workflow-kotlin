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
  api(project(":workflow-ui:core-android"))

  api(Dependencies.AndroidX.Lifecycle.viewModel)
  api(Dependencies.Kotlin.Stdlib.jdk6)

  androidTestImplementation(Dependencies.Test.AndroidX.core)
  androidTestImplementation(Dependencies.Test.AndroidX.truthExt)
}
