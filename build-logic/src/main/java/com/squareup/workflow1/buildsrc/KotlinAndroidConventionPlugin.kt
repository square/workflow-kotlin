package com.squareup.workflow1.buildsrc

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized

class KotlinAndroidConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.plugins.apply("org.jetbrains.kotlin.android")

    target.kotlinCommonSettings(bomConfigurationName = "implementation")

    target.extensions.configure<AndroidComponentsExtension<*, *, *>>("androidComponents") { components ->
      components.onVariants(
        selector = components.selector().withBuildType("debug")
      ) { variant ->
        val nameCaps = variant.name
          .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val testTask = "connected${nameCaps}AndroidTest"
        target.tasks.register("prepare${nameCaps}AndroidTestArtifacts") { task ->
          task.description =
            "Creates all artifacts used in `$testTask` without trying to execute tests."
          task.dependsOn(target.tasks.getByName(testTask).taskDependencies)
        }
      }
    }
  }
}
