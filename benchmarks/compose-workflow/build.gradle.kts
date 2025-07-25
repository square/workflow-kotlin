plugins {
  id("com.android.application")
  id("kotlin-android")
  id("android-defaults")
  alias(libs.plugins.compose.compiler)
}

// Note: We are not including our defaults from .buildscript as we do not need the base Workflow
// dependencies that those include.

android {
  defaultConfig {
    // TODO why isn't this taking?
    testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
  }

  buildTypes {
    debug {
      isDebuggable = false
    }
  }

  namespace = "com.squareup.benchmark.composeworkflow.benchmark"
  testNamespace = "$namespace.test"
}

dependencies {
  androidTestImplementation(project(":workflow-runtime"))
  androidTestImplementation(libs.androidx.benchmark)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.kotlin.test.jdk)
  androidTestImplementation(libs.kotlinx.coroutines.test)
}
