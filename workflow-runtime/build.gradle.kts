import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64

plugins {
  id("kotlin-multiplatform")
  id("published")
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
}

dependencies {
  commonMainApi(project(":workflow-core"))
  commonMainApi(libs.kotlinx.coroutines.core)

  commonTestImplementation(libs.kotlinx.coroutines.test.common)
  commonTestImplementation(libs.kotlin.test.core)
}
