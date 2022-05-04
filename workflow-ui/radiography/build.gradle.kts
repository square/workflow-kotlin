plugins {
  id("com.android.library")
  `kotlin-android`
  id("org.jetbrains.dokka")
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))
apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  // See https://github.com/Kotlin/kotlinx.coroutines/issues/1064#issuecomment-479412940
  packagingOptions.exclude("**/*.kotlin_*")
}

dependencies {
  api(project(":workflow-ui:core-android"))
  api(libs.squareup.radiography)
  api(libs.kotlin.jdk6)

  implementation(project(":workflow-runtime"))
  implementation(libs.androidx.activity.core)
  implementation(libs.androidx.fragment.core)
  implementation(libs.androidx.savedstate)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  androidTestImplementation(project(":workflow-ui:container-android"))
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)
}
