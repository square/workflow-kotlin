import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
  id("android-ui-tests")
  id("published")
}

android {
  buildFeatures.compose = true
  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
  }
  namespace = "com.squareup.workflow1.ui.compose.tooling"
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
  val composeBom = platform(libs.androidx.compose.bom)

  androidTestImplementation(libs.androidx.activity.core)
  androidTestImplementation(composeBom)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.kotlin.test.jdk)

  androidTestImplementation(project(":workflow-runtime"))

  implementation(composeBom)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)

  implementation(project(":workflow-ui:compose"))
  implementation(project(":workflow-ui:core-android"))
  implementation(project(":workflow-ui:core"))
}
