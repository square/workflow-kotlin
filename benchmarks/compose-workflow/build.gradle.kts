import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.version
import com.squareup.workflow1.buildsrc.internal.javaTarget
import com.squareup.workflow1.buildsrc.internal.javaTargetVersion

plugins {
  id("com.android.library")
  id("kotlin-android")
  // id("android-defaults")
  alias(libs.plugins.androidx.benchmark)
  alias(libs.plugins.compose.compiler)
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

    // TODO why isn't this taking?
    testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

    // must be one of: 'None', 'StackSampling', or 'MethodTracing'
    testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "MethodTracing"
    testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
  }

  // buildTypes {
  //   debug {
  //     isDebuggable = false
  //     isProfileable = true
  //   }
  // }

  testBuildType = "release"
  // testBuildType = "debug"
  buildTypes {
    debug {
      // Since isDebuggable can"t be modified by gradle for library modules,
      // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-proguard-rules.pro"
      )
    }
    release {
      isDefault = true
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
