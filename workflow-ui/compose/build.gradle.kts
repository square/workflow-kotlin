import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.library")
  `kotlin-android`
  id("org.jetbrains.dokka")
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))
apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  buildFeatures.compose = true
  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
  }
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf(
      "-opt-in=kotlin.RequiresOptIn"
    )
  }
}

dependencies {
  api(project(":workflow-core"))
  api(project(":workflow-ui:core-android"))
  api(project(":workflow-ui:container-android"))
  api(libs.androidx.compose.foundation)

  implementation(libs.androidx.savedstate)

  androidTestImplementation(project(":workflow-runtime"))
  androidTestImplementation(libs.androidx.activity.core)
  androidTestImplementation(libs.androidx.compose.ui)
  androidTestImplementation(libs.kotlin.test.jdk)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
