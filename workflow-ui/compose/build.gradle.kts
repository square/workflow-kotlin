import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.library")
  kotlin("android")
  id("org.jetbrains.dokka")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

android {
  // See https://github.com/Kotlin/kotlinx.coroutines/issues/1064#issuecomment-479412940
  packagingOptions.exclude("**/*.kotlin_*")

  buildFeatures.compose = true
  composeOptions {
    kotlinCompilerVersion = "1.4.21"
    kotlinCompilerExtensionVersion = "1.0.0-alpha11"
  }
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    useIR = true

    @Suppress("SuspiciousCollectionReassignment")
    freeCompilerArgs += listOf(
      "-Xopt-in=kotlin.RequiresOptIn",
      // Compose alpha11 uses a special release of Kotlin to fix some kotlinx.serialization
      // incompatibilities. We don't care about that. This configuration taken from
      // https://stackoverflow.com/questions/65545018/where-can-i-put-the-suppresskotlinversioncompatibilitycheck-flag
      "-P",
      "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"
    )
  }
}

dependencies {
  api(project(":workflow-core"))
  api(project(":workflow-ui:backstack-android"))
  api(project(":workflow-ui:core-android"))

  api(Dependencies.Kotlin.Stdlib.jdk8)

  androidTestImplementation(project(":workflow-runtime"))
  androidTestImplementation(Dependencies.AndroidX.activity)
  androidTestImplementation(Dependencies.Test.AndroidX.core)
  androidTestImplementation(Dependencies.Test.AndroidX.truthExt)
  androidTestImplementation("androidx.compose.foundation:foundation:1.0.0-alpha11")
  androidTestImplementation("androidx.compose.ui:ui:1.0.0-alpha11")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.0.0-alpha11")
}
