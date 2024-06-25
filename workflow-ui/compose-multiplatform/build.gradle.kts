import com.squareup.workflow1.buildsrc.iosTargets
import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
  alias(libs.plugins.jetbrains.compose.plugin)
  id("kotlin-multiplatform")
  id("com.android.library")
  id("android-defaults")
  id("android-ui-tests")
  // id("published")
}

kotlin {
  val targets = project.findProperty("workflow.targets") ?: "kmp"
  if (targets == "kmp" || targets == "ios") {
    iosTargets()
  }
  if (targets == "kmp" || targets == "jvm") {
    jvm {}
  }
  if (targets == "kmp" || targets == "js") {
    js(IR).browser()
  }
  if (targets == "kmp" || targets == "android") {
    androidTarget()
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":workflow-ui:core"))

      implementation(compose.foundation)
      implementation(compose.components.uiToolingPreview)
      implementation(compose.runtime)
      implementation(compose.ui)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.squareup.okio)
      implementation(libs.jetbrains.lifecycle.runtime.compose)

      implementation(project(":workflow-core"))
      implementation(project(":workflow-runtime"))
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test.jdk)
      implementation(compose.foundation)
      @OptIn(ExperimentalComposeLibrary::class)
      implementation(compose.uiTest)
    }

    androidMain.dependencies {
      api(project(":workflow-ui:core-android"))
      implementation(libs.androidx.activity.core)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.compose.foundation.layout)
      implementation(libs.androidx.compose.runtime.saveable)
      implementation(libs.androidx.lifecycle.common)
      implementation(libs.androidx.lifecycle.core)
    }
  }
}

android {
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures.compose = true
  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
  }
  namespace = "com.squareup.workflow1.ui.compose.multiplatform"
  testNamespace = "$namespace.test"

  dependencies {
    debugImplementation(compose.uiTooling)
  }
}
