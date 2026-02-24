package com.squareup.workflow1.buildsrc

import com.rickbusarow.kgx.libsCatalog
import com.rickbusarow.kgx.pluginId
import com.rickbusarow.kgx.version
import com.squareup.workflow1.buildsrc.internal.invoke
import com.squareup.workflow1.buildsrc.internal.isRunningFromIde
import com.squareup.workflow1.buildsrc.internal.javaLanguageVersion
import com.squareup.workflow1.buildsrc.internal.javaTarget
import com.squareup.workflow1.buildsrc.internal.kotlin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Strict
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.kotlinCommonSettings(bomConfigurationName: String) {
  pluginManager.apply(libsCatalog.pluginId("ktlint"))

  // force the same Kotlin version everywhere, including transitive dependencies
  dependencies {
    add(
      bomConfigurationName,
      platform(kotlin(module = "bom", version = libsCatalog.version("kotlin")))
    )
  }

  extensions.configure(KotlinProjectExtension::class.java) { extension ->
    extension.jvmToolchain { toolChain ->
      toolChain.languageVersion.set(javaLanguageVersion)
    }
  }

  // Gradle 9 defaults failOnNoDiscoveredTests to true, but some modules have test source
  // directories without actual test classes.
  tasks.withType(AbstractTestTask::class.java).configureEach { testTask ->
    testTask.failOnNoDiscoveredTests.set(false)
  }

  tasks.withType(KotlinCompile::class.java).configureEach { kotlinCompile ->
    kotlinCompile.apply {
      if (!(path.startsWith(":samples") || path.startsWith(":benchmarks") ||
          name.contains("test", ignoreCase = true))
      ) {
        explicitApiMode.set(Strict)
      }
    }
    kotlinCompile.compilerOptions {
      jvmTarget.set(JvmTarget.fromTarget(this@kotlinCommonSettings.javaTarget))

      // Allow warnings when running from IDE, makes it easier to experiment.
      if (!isRunningFromIde) {
        allWarningsAsErrors.set(true)
      }

      // Don't panic, all this does is allow us to use the @OptIn meta-annotation.
      // to define our own experiments.
      freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")

      // Make sure our module names don't conflict with those from pre-workflow1
      // releases, so that old and new META-INF/ entries don't stomp each other.
      // (This is only an issue for apps that are still migrating from workflow to
      // workflow1, and so need to import two versions of the library.)
      // https://blog.jetbrains.com/kotlin/2015/09/kotlin-m13-is-out/
      moduleName.set("wf1-${project.name}")
    }
  }
}
