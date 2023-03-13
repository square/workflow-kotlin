package com.squareup.workflow1.buildsrc

import com.squareup.workflow1.buildsrc.internal.libsCatalog
import com.squareup.workflow1.buildsrc.internal.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

class KotlinMultiPlatformConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {

    if (!target.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
      target.plugins.apply("org.jetbrains.kotlin.multiplatform")
    }

    target.tasks.withType(Test::class.java) { test ->
      target.properties
        .asSequence()
        .filter { (key, value) ->
          key.startsWith("workflow.runtime") && value != null
        }
        .forEach { (key, value) ->
          // Add in a system property to the fork for the test.
          test.systemProperty(key, value!!)
        }
    }

    // Sets the JDK target for published artifacts.
    // This takes priority over the java toolchain version.
    target.tasks.withType(JavaCompile::class.java) { javaCompile ->
      javaCompile.options.release.set(target.libsCatalog.version("jdk_target").toInt())
    }

    target.extensions.configure(KotlinProjectExtension::class.java) { kotlin ->
      kotlin.sourceSets.configureEach { sourceSet ->
        sourceSet.languageSettings {
          optIn("kotlin.RequiresOptIn")
        }
      }
    }

    target.kotlinCommonSettings(bomConfigurationName = "commonMainImplementation")
  }
}
