import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  id("kotlin-multiplatform")
  id("published")
  id("app.cash.burst")
}

kotlin {
  val targets = project.findProperty("workflow.targets") ?: "kmp"
  if (targets == "kmp" || targets == "ios") {
    iosWithSimulatorArm64()
  }
  if (targets == "kmp" || targets == "jvm") {
    jvm {}
  }
  if (targets == "kmp" || targets == "js") {
    js(IR) { browser() }
  }

  // Needed for expect class Lock, which is not public API, so this doesn't add any binary compat
  // risk.
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}

dependencies {
  commonMainApi(project(":workflow-core"))
  commonMainApi(libs.kotlinx.coroutines.core)

  commonTestImplementation(libs.kotlinx.coroutines.test.common)
  commonTestImplementation(libs.kotlin.test.core)
}
