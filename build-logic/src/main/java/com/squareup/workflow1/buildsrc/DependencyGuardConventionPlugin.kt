package com.squareup.workflow1.buildsrc

import com.android.build.gradle.TestedExtension
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class DependencyGuardConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {

    // We have to use `afterEvaluate { ... }` because both KMP and AGP create their configurations
    // later in the configuration phase.  If we were to try looking up those configurations eagerly
    // as soon as this convention plugin is applied, there would be nothing there.
    target.afterEvaluate {
      val configurationNames = when {
        // record the root project's *build* classpath
        target == target.rootProject -> listOf("classpath")

        // For Android modules, just hard-code `releaseRuntimeClasspath` for the release variant.
        // This is actually pretty robust, since if this configuration ever changes,
        // dependency-guard will fail when trying to look it up.
        target.extensions.findByType(TestedExtension::class.java) != null -> listOf(
          "releaseRuntimeClasspath"
        )

        // If we got here, we're either in an empty "parent" module without a build plugin (and no
        // configurations), or we're in a vanilla Kotlin module.  In this case, we can just look at
        // configuration names.
        else ->
          target.configurations
            .map { it.name }
            .filter {
              it.endsWith("runtimeClasspath", ignoreCase = true) &&
                !it.endsWith("testRuntimeClasspath", ignoreCase = true)
            }
      }

      if (configurationNames.isNotEmpty()) {
        target.plugins.apply("com.dropbox.dependency-guard")

        target.extensions.configure(DependencyGuardPluginExtension::class.java) {
          configurationNames.forEach { configName ->
            // Tell dependency-guard to check the `configName` configuration's dependencies.
            it.configuration(configName)
          }
        }
      }
    }
  }
}
