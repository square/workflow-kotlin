import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64

plugins {
  `kotlin-multiplatform`
  published
}

kotlin {
  val targets = project.findProperty("workflow.targets") ?: "kmp"
  if (targets == "kmp" || targets == "ios") {
    iosWithSimulatorArm64(project)
  }
  if (targets == "kmp" || targets == "jvm") {
    jvm { withJava() }
  }
  if (targets == "kmp" || targets == "js") {
    js { browser() }
  }
}

dependencies {
  commonMainApi(libs.kotlin.stdlib)
  commonMainApi(libs.kotlinx.coroutines.core)
  // For Snapshot.
  commonMainApi(libs.squareup.okio)

  commonTestImplementation(libs.kotlinx.atomicfu)
  commonTestImplementation(libs.kotlinx.coroutines.test.common)
  commonTestImplementation(libs.kotlin.test.jdk)
}
