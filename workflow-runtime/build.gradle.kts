import com.android.build.api.dsl.androidLibrary
import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
  // This is the new/future plugin for Android in KMP. com.android.library is going away.
  // See https://developer.android.com/kotlin/multiplatform/plugin
  id("com.android.kotlin.multiplatform.library")
  // This is our own convention plugin, not the standard one.
  id("kotlin-multiplatform")
  id("published")
  id("app.cash.burst")
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.compose.compiler)
}

// Configure dependency resolution to prefer desktop variants for JVM target
configurations.all {
  attributes {
    // When resolving for JVM, prefer the desktop (non-Android) variants of Compose
    attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
  }
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
    @Suppress("UnstableApiUsage")
    androidLibrary {
      namespace = "com.squareup.workflow1.android"
      testNamespace = "$namespace.test"

      compileSdk = libs.versions.compileSdk.get().toInt()
      minSdk = libs.versions.minSdk.get().toInt()

      withDeviceTest {
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

        // These become aliases to the androidx runtime libraries in Compose 1.9.3.
        implementation(libs.jetbrains.compose.runtime)
        implementation(libs.jetbrains.compose.runtime.saveable)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlinx.coroutines.test.common)
        implementation(libs.kotlin.test.core)
      }
    }

    if (targets == "kmp" || targets == "android") {
      androidMain {
        dependencies {
          // Add Android-specific dependencies here. Note that this source set depends on
          // commonMain by default and will correctly pull the Android artifacts of any KMP
          // dependencies declared in commonMain.

          api(libs.androidx.lifecycle.viewmodel.savedstate)

          implementation(libs.jetbrains.compose.ui)
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
          implementation(libs.burst)
        }
      }
    }
  }
}
