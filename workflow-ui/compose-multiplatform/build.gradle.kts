import com.squareup.workflow1.buildsrc.iosTargets
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithPresetFunctions
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
  alias(libs.plugins.jetbrains.compose.plugin)
  id("kotlin-multiplatform")
  id("com.android.library")
  id("android-defaults")
  id("android-ui-tests")
  // id("published")
}

fun KotlinTargetContainerWithPresetFunctions.androidTargetWithTesting() {
  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    instrumentedTestVariant {
      sourceSetTree.set(KotlinSourceSetTree.test)

      dependencies {
        debugImplementation(libs.androidx.compose.ui.test.manifest)
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.squareup.leakcanary.instrumentation)
        implementation(project(":workflow-ui:internal-testing-android"))
        implementation(project.dependencies.platform(libs.androidx.compose.bom))
      }
    }
  }
}

kotlin {
  val targets = project.findProperty("workflow.targets") ?: "kmp"

  listOf(
    "ios" to { iosTargets() },
    "jvm" to { jvm {} },
    "js" to { js(IR).browser() },
    "android" to { androidTargetWithTesting() }
  ).forEach { (target, action) ->
    if (targets == "kmp" || targets == target) {
      action()
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":workflow-ui:core"))

      implementation(compose.foundation)
      implementation(compose.components.uiToolingPreview)
      implementation(compose.runtime)
      implementation(compose.ui)
      implementation(libs.kotlinx.coroutines.core)
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
    }
  }
}

android {
  val name = "com.squareup.workflow1.ui.compose.multiplatform"
  val testName = "$name.test"

  defaultConfig {
    testApplicationId = testName
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures.compose = true
  composeOptions.kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
  namespace = name
  testNamespace = testName
  testOptions.animationsDisabled = true

  dependencies {
    debugImplementation(compose.uiTooling)
  }
}
