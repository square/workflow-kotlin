import com.squareup.workflow1.buildsrc.iosTargets
import org.gradle.api.JavaVersion.VERSION_1_9
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithPresetFunctions
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
  alias(libs.plugins.jetbrains.compose.plugin)
  id("kotlin-multiplatform")
  id("com.android.application")
  id("android-defaults")
  id("android-ui-tests")
  id("compose-ui-tests")
}

kotlin {
  val targets = project.findProperty("workflow.targets") ?: "kmp"

  listOf(
    "ios" to {
      iosTargets().forEach { iosTarget ->
        // This allows us to import ComposeApp into the iOS project
        iosTarget.binaries.framework {
          baseName = "ComposeApp"
          isStatic = true
        }
      }
    },
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
      implementation(compose.runtime)
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

    val iosX64Main by getting
    val iosArm64Main by getting
    val iosSimulatorArm64Main by getting

    val iosMain by creating {
      dependsOn(commonMain.get())
      iosX64Main.dependsOn(this)
      iosArm64Main.dependsOn(this)
      iosSimulatorArm64Main.dependsOn(this)
    }

    val nonAndroidMain by creating {
      dependsOn(commonMain.get())
      iosMain.dependsOn(this)
      appleMain.get().dependsOn(this)
      jsMain.get().dependsOn(this)
      jvmMain.get().dependsOn(this)
    }

    // Currently just used to make a noop BackHandler
    val nonMobileMain by creating {
      appleMain.get().dependsOn(this)
      jsMain.get().dependsOn(this)
      jvmMain.get().dependsOn(this)
      dependsOn(commonMain.get())
    }
  }
}

/**
 * All of these are needed due needing Java 9 whenever databinding is enabled and the release
 * flag is set. See [com.android.build.gradle.tasks.JavaCompileUtils::checkReleaseFlag]. Setting
 * the language version here fixes the issue.
 * TODO: Figure out how to disable the release flag for the sample app
 */
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions.jvmTarget = "9"
}

android {
  compileOptions {
    sourceCompatibility = VERSION_1_9
    targetCompatibility = VERSION_1_9
  }

  buildFeatures {
    viewBinding = true
    // This is needed for the layout inspector to work in Kotlin 2.0.0 even though we need it for
    // the current Kotlin version we should leave this here when we upgrade to 2.0.0
    compose = true
  }

  defaultConfig {
    applicationId = "com.squareup.sample.compose"
  }

  // In Kotlin 2.0.0 this isn't strictly necessary but it is necessary for the layout inspector to
  // work in Kotlin 2.0.0
  composeOptions.kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
  namespace = "com.squareup.sample.compose"

  dependencies {
    debugImplementation(compose.uiTooling)
  }
}

fun KotlinTargetContainerWithPresetFunctions.androidTargetWithTesting() {
  androidTarget {
    compilations.all {
      kotlinOptions {
        jvmTarget = "9"
      }
    }
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
