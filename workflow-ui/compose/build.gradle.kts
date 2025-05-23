import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.library")
  id("kotlin-android")
  id("android-defaults")
  id("android-ui-tests")
  id("published")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.squareup.workflow1.ui.compose"
  testNamespace = "$namespace.test"
}

tasks.withType<KotlinCompile> {
  compilerOptions.apply {
    freeCompilerArgs.addAll(
      listOf(
        "-opt-in=kotlin.RequiresOptIn"
      )
    )
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)

  androidTestImplementation(libs.androidx.activity.core)
  androidTestImplementation(composeBom)
  androidTestImplementation(libs.androidx.compose.foundation)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.truth)
  androidTestImplementation(libs.kotlin.test.jdk)

  androidTestImplementation(project(":workflow-ui:internal-testing-compose"))

  api(libs.androidx.compose.runtime)
  api(libs.kotlin.common)

  api(project(":workflow-ui:core-android"))
  api(project(":workflow-ui:core-common"))

  implementation(composeBom)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.runtime.saveable)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.lifecycle.common)
  implementation(libs.androidx.lifecycle.compose)
  implementation(libs.androidx.lifecycle.core)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.squareup.okio)

  implementation(project(":workflow-core"))
  implementation(project(":workflow-runtime"))
}
