import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
  alias(libs.plugins.jetbrains.compose.plugin)
  id("kotlin-multiplatform")
  id("com.android.application")
  id("compose-multiplatform-ui-tests")
}

kotlin {
  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    instrumentedTestVariant {
      sourceSetTree.set(KotlinSourceSetTree.test)

      dependencies {
        implementation(project.dependencies.platform(libs.androidx.compose.bom))
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.squareup.leakcanary.instrumentation)
        implementation(project(":workflow-ui:internal-testing-android"))
        debugImplementation(libs.androidx.compose.ui.test.manifest)
      }
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.foundation)
      implementation(compose.material)
      implementation(compose.preview)
      implementation(project(":workflow-ui:compose-multiplatform"))
      implementation(libs.kotlin.test.core)
    }
  }
}

android {
  val name = "com.squareup.sample.compose.multiplatform"
  defaultConfig {
    applicationId = name
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
  }
  testOptions {
    animationsDisabled = true
  }
  namespace = name
}
