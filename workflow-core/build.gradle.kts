import com.squareup.workflow1.buildsrc.iosTargets

plugins {
  id("kotlin-multiplatform")
  id("published")
}

kotlin {
  targets.all {
    compilations.all {
      compileTaskProvider.configure {
        compilerOptions {
          freeCompilerArgs.add("-Xexpect-actual-classes")
        }
      }
    }
  }

  val targets = project.findProperty("workflow.targets") ?: "kmp"
  if (targets == "kmp" || targets == "ios") {
    iosTargets()
  }
  if (targets == "kmp" || targets == "jvm") {
    jvm { withJava() }
  }
  if (targets == "kmp" || targets == "js") {
    js(IR) { browser() }
  }
}

dependencies {
  commonMainApi(libs.kotlin.jdk6)
  commonMainApi(libs.kotlinx.coroutines.core)
  // For Snapshot.
  commonMainApi(libs.squareup.okio)

  commonTestImplementation(libs.kotlinx.atomicfu)
  commonTestImplementation(libs.kotlinx.coroutines.test.common)
  commonTestImplementation(libs.kotlin.test.jdk)
}
