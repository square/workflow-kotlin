import org.jetbrains.dokka.gradle.DokkaTask
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

buildscript {
  dependencies {
    classpath(libs.android.gradle.plugin)
    classpath(libs.kotlinx.benchmark.gradle.plugin)
    classpath(libs.dokka.gradle.plugin)
    classpath(libs.kotlin.serialization.gradle.plugin)
    classpath(libs.kotlinx.binaryCompatibility.gradle.plugin)
    classpath(libs.kotlin.gradle.plugin)
    classpath(libs.google.ksp)
    classpath(libs.ktlint.gradle)
    classpath(libs.vanniktech.publish)
  }

  repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
    // For binary compatibility validator.
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
  }
}

plugins {
  base
  `artifacts-check`
  `dependency-guard`
}

subprojects {

  apply(plugin = "org.jlleitschuh.gradle.ktlint")
  afterEvaluate {
    configurations.configureEach {
      // There could be transitive dependencies in tests with a lower version. This could cause
      // problems with a newer Kotlin version that we use.
      resolutionStrategy.force(libs.kotlin.reflect)
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
  }
}

apply(from = rootProject.file(".buildscript/binary-validation.gradle"))

// This plugin needs to be applied to the root projects for the dokkaGfmCollector task we use to
// generate the documentation site.
apply(plugin = "org.jetbrains.dokka")

// Configuration that applies to all dokka tasks, both those used for generating javadoc artifacts
// and the documentation site.
subprojects {
  tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets.configureEach {
      reportUndocumented.set(false)
      skipDeprecated.set(true)
      jdkVersion.set(8)

      // TODO(#124) Add source links.

      perPackageOption {
        // Will match all .internal packages and sub-packages, regardless of module.
        matchingRegex.set(""".*\.internal.*""")
        suppress.set(true)
      }
    }
  }
}

allprojects {

  configurations.all {
    resolutionStrategy.eachDependency {
      // This ensures that any time a dependency has a transitive dependency upon androidx.lifecycle,
      // it uses the same version as the rest of the project.  This is crucial, since Androidx
      // libraries are never in sync and lifecycle 2.4.0 introduced api-breaking changes.
      if (requested.group == "androidx.lifecycle") {
        useVersion(libs.versions.androidx.lifecycle.get())
      }
    }
  }
}

// This task is invoked by the documentation site generator script in the main workflow project (not
// in this repo), which also expects the generated files to be in a specific location. Both the task
// name and destination directory are defined in this script:
// https://github.com/square/workflow/blob/main/deploy_website.sh
tasks.register<Copy>("siteDokka") {
  description = "Generate dokka Github-flavored Markdown for the documentation site."
  group = "documentation"
  dependsOn(":dokkaGfmCollector")

  // Copy the files instead of configuring a different output directory on the dokka task itself
  // since the default output directories disambiguate between different types of outputs, and our
  // custom directory doesn't.
  from(buildDir.resolve("dokka/gfmCollector/workflow"))
  into(buildDir.resolve("dokka/workflow"))
}
