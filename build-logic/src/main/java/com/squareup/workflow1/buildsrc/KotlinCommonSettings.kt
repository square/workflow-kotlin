package com.squareup.workflow1.buildsrc

import com.squareup.workflow1.buildsrc.internal.invoke
import com.squareup.workflow1.buildsrc.internal.isRunningFromIde
import com.squareup.workflow1.buildsrc.internal.kotlin
import com.squareup.workflow1.buildsrc.internal.libsCatalog
import com.squareup.workflow1.buildsrc.internal.version
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.kotlinCommonSettings(bomConfigurationName: String) {
  applyKtLint()

  // force the same Kotlin version everywhere, including transitive dependencies
  dependencies {
    add(bomConfigurationName, platform(kotlin("bom")))
  }

  extensions.configure(KotlinProjectExtension::class.java) { extension ->
    extension.jvmToolchain { toolChain ->
      toolChain.languageVersion.set(JavaLanguageVersion.of(libsCatalog.version("jdk-toolchain")))
    }
  }

  tasks.withType(KotlinCompile::class.java) { kotlinCompile ->
    kotlinCompile.kotlinOptions {
      val targetInt = libsCatalog.version("jdk-target").toInt()
      jvmTarget = when {
        targetInt < 9 -> "1.$targetInt"
        else -> "$targetInt"
      }

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

    maybeEnableExplicitApi(kotlinCompile)
  }
}

private fun Project.maybeEnableExplicitApi(compileTask: KotlinCompile) {
  when {
    path.startsWith(":samples") -> return
    path.startsWith(":benchmarks") -> return
    compileTask.name.contains("test", ignoreCase = true) -> return
    else -> compileTask.kotlinOptions {
      // TODO this should be moved to `kotlin { explicitApi() }` once that's working for android
      //  projects, see https://youtrack.jetbrains.com/issue/KT-37652.
      freeCompilerArgs += "-Xexplicit-api=strict"
    }
  }
}
