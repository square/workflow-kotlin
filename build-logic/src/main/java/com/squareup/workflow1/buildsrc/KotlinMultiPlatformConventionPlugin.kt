package com.squareup.workflow1.buildsrc

import com.squareup.workflow1.buildsrc.internal.javaTargetInt
import com.squareup.workflow1.buildsrc.internal.javaTargetVersion
import java.time.Duration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

class KotlinMultiPlatformConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {

    if (!target.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
      target.plugins.apply("org.jetbrains.kotlin.multiplatform")
    }

    target.tasks.withType(Test::class.java) { test ->
      target.properties
        .asSequence()
        .filter { (key, value) -> key.startsWith("workflow.runtime") && value != null }
        .forEach { (key, value) ->
          // Add in a system property to the fork for the test.
          test.systemProperty(key, value!!)
        }
    }

    // Kotlin/Native test binaries have no per-test timeout, so one deadlocked test hangs the task
    // forever. On CI that meant the "iOS Unit Tests for KMP Modules" job ran until its 45-minute
    // limit and was cancelled without producing a test report. The whole workflow-runtime iOS suite
    // takes well under a minute on CI, so anything approaching this limit is a hang. Failing the
    // task instead lets the report and any partial results be uploaded.
    target.tasks.withType(KotlinNativeTest::class.java).configureEach { test ->
      test.timeout.set(Duration.ofMinutes(15))
    }

    // Sets the JDK target for published artifacts.
    // This takes priority over the java toolchain version.
    target.tasks.withType(JavaCompile::class.java).configureEach { javaCompile ->
      javaCompile.options.release.set(target.javaTargetInt)
    }
    target.extensions.configure(KotlinProjectExtension::class.java) { kotlin ->
      kotlin.sourceSets.configureEach { sourceSet ->
        sourceSet.languageSettings { optIn("kotlin.RequiresOptIn") }
      }
    }
    target.extensions.configure(JavaPluginExtension::class.java) { java ->
      java.sourceCompatibility = target.javaTargetVersion
      java.targetCompatibility = target.javaTargetVersion
    }

    target.kotlinCommonSettings(bomConfigurationName = "commonMainImplementation")
  }
}
