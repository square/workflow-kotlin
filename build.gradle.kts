/*
 * Copyright 2017 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

buildscript {
  dependencies {
    classpath(Dependencies.android_gradle_plugin)
    classpath(Dependencies.detekt)
    classpath(Dependencies.dokka)
    classpath(Dependencies.Kotlin.binaryCompatibilityValidatorPlugin)
    classpath(Dependencies.Kotlin.gradlePlugin)
    classpath(Dependencies.ktlint)
    classpath(Dependencies.mavenPublish)
  }

  repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
    // For Kotlin 1.4.
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    // For binary compatibility validator.
    maven("https://kotlin.bintray.com/kotlinx")
  }
}

// See https://stackoverflow.com/questions/25324880/detect-ide-environment-with-gradle
val isRunningFromIde get() = project.properties["android.injected.invoked.from.ide"] == "true"

subprojects {
  repositories {
    google()
    mavenCentral()
    jcenter()
    // For Kotlin 1.4.
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
  }

  apply(plugin = "org.jlleitschuh.gradle.ktlint")
  apply(plugin = "io.gitlab.arturbosch.detekt")
  afterEvaluate {
    tasks.findByName("check")
      ?.dependsOn("detekt")

    configurations.configureEach {
      // There could be transitive dependencies in tests with a lower version. This could cause
      // problems with a newer Kotlin version that we use.
      resolutionStrategy.force(Dependencies.Kotlin.reflect)
    }
  }

  tasks.withType<KotlinCompile>() {
    kotlinOptions {
      // Allow warnings when running from IDE, makes it easier to experiment.
      if (!isRunningFromIde) {
        allWarningsAsErrors = true
      }

      jvmTarget = "1.8"

      // Don't panic, all this does is allow us to use the @OptIn meta-annotation.
      // to define our own experiments, and some required args for compose dev15 taken from
      // https://developer.android.com/jetpack/androidx/releases/compose-runtime
      freeCompilerArgs += listOf(
        "-Xopt-in=kotlin.RequiresOptIn",
        "-Xallow-jvm-ir-dependencies",
        "-Xskip-prerelease-check"
      )

    }
  }

  // Configuration documentation: https://github.com/JLLeitschuh/ktlint-gradle#configuration
  configure<KtlintExtension> {
    // Prints the name of failed rules.
    verbose.set(true)
    reporters {
      // Default "plain" reporter is actually harder to read.
      reporter(ReporterType.JSON)
    }

    disabledRules.set(
      setOf(
        // IntelliJ refuses to sort imports correctly.
        // This is a known issue: https://github.com/pinterest/ktlint/issues/527
        "import-ordering",
        // Ktlint doesn't know how to handle nullary annotations on function types, e.g.
        // @Composable () -> Unit.
        "paren-spacing"
      )
    )
  }

  configure<DetektExtension> {
    config = files("${rootDir}/detekt.yml")
    // Treat config file as an override for the default config.
    buildUponDefaultConfig = true
  }
}

apply(from = rootProject.file(".buildscript/binary-validation.gradle"))
