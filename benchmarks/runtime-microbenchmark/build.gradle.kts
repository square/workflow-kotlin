import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.version
import com.squareup.workflow1.buildsrc.internal.javaTarget
import com.squareup.workflow1.buildsrc.internal.javaTargetVersion

plugins {
  // Must be applied before kotlin-android so the convention can detect that this is a benchmark.
  alias(libs.plugins.androidx.benchmark)
  id("com.android.library")
  id("kotlin-android")
  id("app.cash.burst")
}

// Note: We are not including our defaults from .buildscript as we do not need the base Workflow
// dependencies that those include.

android {
  compileSdk = libsCatalog.version("compileSdk").toInt()

  compileOptions {
    sourceCompatibility = javaTargetVersion
    targetCompatibility = javaTargetVersion
  }

  kotlinOptions {
    jvmTarget = javaTarget
    freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
  }

  defaultConfig {
    minSdk = 28
    targetSdk = libsCatalog.version("targetSdk").toInt()

    testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

    // This flag is supposed to enable dry run in the test runner, and it does so locally, but for
    // some reason it doesn't seem to be working in CI.
    val benchmarkDryRunEnabled = project.findProperty("androidx.benchmark.dryRunMode.enable")
    if (benchmarkDryRunEnabled == "true") {
      println("Running benchmarks in dry mode: emulator allowed, no measurements taken, no warmup.")
      testInstrumentationRunnerArguments["androidx.benchmark.dryRunMode.enable"] = "true"
    } else {
      // must be one of: 'None', 'StackSampling', or 'MethodTracing'
      testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "MethodTracing"
      testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
    }
  }

  testBuildType = "release"
  buildTypes {
    debug {
      // Since isDebuggable can"t be modified by gradle for library modules,
      // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "benchmark-proguard-rules.pro"
      )
    }
    release {
      isDefault = true
    }
  }

  namespace = "com.squareup.benchmark.runtime.benchmark"
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
