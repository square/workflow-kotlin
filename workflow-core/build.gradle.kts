import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64

plugins {
  id("kotlin-multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("published")
}

kotlin {
  val targets = project.findProperty("workflow.targets") ?: "kmp"
  if (targets == "kmp" || targets == "ios") {
    iosWithSimulatorArm64()
  }
  if (targets == "kmp" || targets == "jvm") {
    jvm { withJava() }
  }
  // The default KMP
  // ["hierarchy template"](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-hierarchy.html#see-the-full-hierarchy-template)
  // configures `androidMain` and `jvmMain` to be entirely separate targets, even though Android
  // *can* be made to be a child of JVM. Changing this requires completely wiring up all targets
  // ourselves though, so for now we've left them separate to simplify gradle config. If there ends
  // up being too much code duplication, we can either make `androidMain` a child of `jvmMain`, or
  // introduce a new shared target that includes both of them. Compose, for example, uses a
  // structure where `jvm` is the shared parent of both `android` and `desktop`.
  if (targets == "kmp" || targets == "android") {
    androidLibrary {
      namespace = "com.squareup.workflow1"
      testNamespace = "$namespace.test"

      compileSdk = libs.versions.compileSdk.get().toInt()
      minSdk = libs.versions.minSdk.get().toInt()

      withHostTestBuilder {
      }.configure {
      }

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
        api(libs.kotlin.jdk6)
        api(libs.kotlinx.coroutines.core)
        // For Snapshot.
        api(libs.squareup.okio)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlinx.atomicfu)
        implementation(libs.kotlinx.coroutines.test.common)
        implementation(libs.kotlin.test.core)
      }
    }

    getByName("androidHostTest") {
      dependencies {
        implementation(libs.robolectric)
        implementation(libs.robolectric.annotations)
      }
    }
  }
}
