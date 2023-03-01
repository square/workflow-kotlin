import com.squareup.workflow1.libsCatalog
import com.squareup.workflow1.version

plugins {
  id("com.android.test")
  id("org.jetbrains.kotlin.android")
}

// Note: We are not including our defaults from .buildscript as we do not need the base Workflow
// dependencies that those include.

android {
  compileSdk = libsCatalog.version("compileSdk").toInt()

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
    jvmTarget = "1.8"
    freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
  }

  defaultConfig {
    minSdk = 26
    targetSdk = libsCatalog.version("targetSdk").toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    // This benchmark buildType is used for benchmarking, and should function like your
    // release build (for example, with minification on). It's signed with a debug key
    // for easy local/CI testing.
    create("benchmark") {
      isDebuggable = true
      signingConfig = getByName("debug").signingConfig
      matchingFallbacks.add("release")
      proguardFile("baseline-proguard-rules.pro")
    }
  }

  // Unclear: there are kotlin_builtins duplication between 1.5.20 and 1.6.10?
  packagingOptions {
    resources.excludes.add("**/*.kotlin_*")
  }

  targetProjectPath = ":benchmarks:performance-poetry:complex-poetry"
  namespace = "com.squareup.benchmarks.performance.poetry.complex.benchmark"
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
  implementation(libs.androidx.macro.benchmark)
  implementation(libs.androidx.test.espresso.core)
  implementation(libs.androidx.test.junit)
  implementation(libs.androidx.test.uiautomator)

  implementation(project(":benchmarks:performance-poetry:complex-poetry"))
  implementation(project(":samples:containers:poetry"))
  implementation(project(":workflow-core"))
}

androidComponents {
  beforeVariants(selector().all()) {
    it.enable = it.buildType == "benchmark"
  }
}
