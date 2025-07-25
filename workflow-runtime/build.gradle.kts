import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64

plugins {
  id("kotlin-multiplatform")
  id("published")
  id("app.cash.burst")
  alias(libs.plugins.compose.compiler)
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
  compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}

dependencies {
  commonMainApi(project(":workflow-core"))
  commonMainApi(libs.kotlinx.coroutines.core)

  commonTestImplementation(libs.kotlinx.coroutines.test.common)
  commonTestImplementation(libs.kotlin.test.core)
}
