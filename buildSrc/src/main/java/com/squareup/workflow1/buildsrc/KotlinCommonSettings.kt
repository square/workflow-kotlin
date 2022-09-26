package com.squareup.workflow1.buildsrc

import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// See https://stackoverflow.com/questions/25324880/detect-ide-environment-with-gradle
val Project.isRunningFromIde
  get() = properties["android.injected.invoked.from.ide"] == "true"

@Suppress("SuspiciousCollectionReassignment")
fun Project.kotlinCommonSettings(
  bomConfigurationName: String
) {

  pluginManager.apply("dokka")

  applyKtLint()

  // force the same Kotlin version everywhere, including transitive dependencies
  dependencies {
    bomConfigurationName(platform(kotlin("bom")))
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions {

      jvmTarget = "1.8"

      // Allow warnings when running from IDE, makes it easier to experiment.
      if (!isRunningFromIde) {
        allWarningsAsErrors = true
      }

      // Don't panic, all this does is allow us to use the @OptIn meta-annotation.
      // to define our own experiments.
      freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"

      // Make sure our module names don't conflict with those from pre-workflow1
      // releases, so that old and new META-INF/ entries don't stomp each other.
      // (This is only an issue for apps that are still migrating from workflow to
      // workflow1, and so need to import two versions of the library.)
      // https://blog.jetbrains.com/kotlin/2015/09/kotlin-m13-is-out/
      moduleName = "wf1-${project.name}"
    }

    maybeEnableExplicitApi(this@withType)
  }
}

private fun Project.maybeEnableExplicitApi(compileTask: KotlinCompile) {
  when {
    path.startsWith(":samples") -> return
    path.startsWith(":benchmarks") -> return
    compileTask.name.contains("test", ignoreCase = true) -> return
    compileTask.name.contains("jmh", ignoreCase = true) -> return
    else -> compileTask.kotlinOptions {
      // TODO this should be moved to `kotlin { explicitApi() }` once that's working for android
      //  projects, see https://youtrack.jetbrains.com/issue/KT-37652.
      freeCompilerArgs += "-Xexplicit-api=strict"
    }
  }
}
