plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("android-defaults")
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
      // TODO why isn't this available?
      // isDebuggable = false
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
}
