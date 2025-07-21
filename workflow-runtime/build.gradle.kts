import com.android.build.api.dsl.androidLibrary
import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64

plugins {
  id("kotlin-multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("published")
  id("app.cash.burst")
}

kotlin {
  // Needed for expect class Lock, which is not public API, so this doesn't add any binary compat
  // risk.
  compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

  val targets = project.findProperty("workflow.targets") ?: "kmp"
  if (targets == "kmp" || targets == "ios") {
    iosWithSimulatorArm64()
  }
  if (targets == "kmp" || targets == "jvm") {
    jvm {}
  }
  if (targets == "kmp" || targets == "android") {
    androidLibrary {
      namespace = "com.squareup.workflow1.android"
      testNamespace = "$namespace.test"

      compileSdk = libs.versions.compileSdk.get().toInt()
      minSdk = libs.versions.minSdk.get().toInt()

      withDeviceTestBuilder {
        sourceSetTreeName = "test"
      }.configure {
        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Disable transition and rotation animations.
        animationsDisabled = true
      }
    }
  }
  if (targets == "kmp" || targets == "js") {
    js(IR) { browser() }
  }

  sourceSets {
    commonMain {
      dependencies {
        api(project(":workflow-core"))
        api(libs.kotlinx.coroutines.core)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlinx.coroutines.test.common)
        implementation(libs.kotlin.test.core)
      }
    }

    androidMain {
      dependencies {
        // Add Android-specific dependencies here. Note that this source set depends on
        // commonMain by default and will correctly pull the Android artifacts of any KMP
        // dependencies declared in commonMain.
        val composeBom = project.dependencies.platform(libs.androidx.compose.bom)

        api(libs.androidx.compose.ui.android)
        api(libs.androidx.lifecycle.viewmodel.savedstate)

        implementation(composeBom)
      }
    }

    getByName("androidDeviceTest") {
      dependencies {
        implementation(project(":workflow-ui:internal-testing-android"))

        implementation(libs.androidx.test.espresso.core)
        implementation(libs.androidx.test.junit)
        implementation(libs.squareup.leakcanary.instrumentation)

        implementation(libs.androidx.activity.ktx)
        implementation(libs.androidx.lifecycle.viewmodel.ktx)
        implementation(libs.androidx.test.core)
        implementation(libs.androidx.test.truth)
        implementation(libs.kotlin.test.core)
        implementation(libs.kotlin.test.jdk)
        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.squareup.papa)
      }
    }
  }
}
