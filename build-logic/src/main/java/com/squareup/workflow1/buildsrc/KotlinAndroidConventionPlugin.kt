package com.squareup.workflow1.buildsrc

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinAndroidConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.apply("org.jetbrains.kotlin.android")

    target.kotlinCommonSettings(bomConfigurationName = "implementation")

    target.extensions.configure<AndroidComponentsExtension<*, *, *>>("androidComponents") { components ->
      val isMicrobenchmarkProject = target.plugins.hasPlugin("androidx.benchmark")
      val buildType = if (isMicrobenchmarkProject) {
        // Microbenchmarks are special, they only run as release.
        "release"
      } else {
        "debug"
      }

      components.onVariants(
        selector = components.selector().withBuildType(buildType)
      ) { variant ->
        val nameCaps = variant.name
          .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val testTaskName = "connected${nameCaps}AndroidTest"
        target.tasks.register("prepare${nameCaps}AndroidTestArtifacts") { task ->
          task.description =
            "Creates all artifacts used in `$testTaskName` without trying to execute tests."
          task.dependsOn(target.tasks.getByName(testTaskName).taskDependencies)
        }
      }
    }
  }
}
