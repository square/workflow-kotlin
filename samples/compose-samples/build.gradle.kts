import com.squareup.workflow1.buildsrc.iosTargets
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithPresetFunctions
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.jetbrains.compose.plugin)
  id("kotlin-multiplatform")
  id("com.android.application")
  id("android-defaults")
  id("android-ui-tests")
  // id("android-sample-app")
  // id("android-ui-tests")
  // id("compose-ui-tests")
}

kotlin {
  val targets = project.findProperty("workflow.targets") ?: "kmp"

  listOf(
    "ios" to { iosTargets() },
    "jvm" to { jvm() },
    "js" to { js(IR).browser() },
    "android" to { androidTargetWithTesting() },
  ).forEach { (target, action) ->
    if (targets == "kmp" || targets == target) {
      action()
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.components.uiToolingPreview)
      implementation(compose.foundation)
      implementation(compose.material)
      implementation(compose.ui)

      implementation(project(":workflow-core"))
      implementation(project(":workflow-runtime"))
      implementation(project(":workflow-ui:compose"))
      implementation(project(":workflow-ui:core"))
    }

    androidMain.dependencies {
      implementation(project.dependencies.platform(libs.androidx.compose.bom))
      implementation(libs.androidx.appcompat)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.activity.core)
      implementation(libs.androidx.compose.foundation)
      implementation(libs.androidx.compose.foundation.layout)
      implementation(libs.androidx.compose.material)
      implementation(libs.androidx.compose.runtime)
      implementation(libs.androidx.compose.ui)
      implementation(libs.androidx.compose.ui.geometry)
      implementation(libs.androidx.compose.ui.graphics)
      implementation(libs.androidx.compose.ui.tooling)
      implementation(libs.androidx.compose.ui.tooling.preview)
      implementation(libs.androidx.lifecycle.viewmodel.ktx)
      implementation(libs.androidx.lifecycle.viewmodel.savedstate)
      implementation(libs.androidx.viewbinding)
      implementation(libs.kotlin.common)
      // For the LayoutInspector.
      implementation(libs.kotlin.reflect)

      implementation(project(":workflow-config:config-android"))
      implementation(project(":workflow-ui:compose-tooling"))
      implementation(project(":workflow-ui:core-android"))
      implementation(project(":workflow-ui:core-common"))
    }

    applyDefaultHierarchyTemplate()

    val nonAndroidMain by creating {
      dependsOn(commonMain.get())
      appleMain.get().dependsOn(this)
      jsMain.get().dependsOn(this)
      jvmMain.get().dependsOn(this)
    }
  }
}

android {
  buildFeatures.viewBinding = true
  defaultConfig {
    applicationId = "com.squareup.sample.compose"
  }
  buildFeatures.compose = true
  composeOptions.kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
  namespace = "com.squareup.sample.compose"

  dependencies {
    debugImplementation(compose.uiTooling)
  }
}

fun KotlinTargetContainerWithPresetFunctions.androidTargetWithTesting() {
  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    instrumentedTestVariant {
      sourceSetTree.set(KotlinSourceSetTree.test)

      dependencies {
        androidTestImplementation(platform(libs.androidx.compose.bom))
        androidTestImplementation(libs.androidx.activity.core)
        androidTestImplementation(libs.androidx.compose.ui)
        androidTestImplementation(libs.androidx.compose.ui.test.junit4)
        androidTestImplementation(libs.androidx.test.core)
        androidTestImplementation(libs.androidx.test.truth)
        androidTestImplementation(libs.kotlin.test.jdk)

        androidTestImplementation(project(":workflow-runtime"))

        debugImplementation(libs.squareup.leakcanary.android)
      }
    }
  }
}

android.compileOptions.targetCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

tasks.withType<KotlinCompile> {
  compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}
