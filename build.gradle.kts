import com.squareup.workflow1.buildsrc.applyKtLint

buildscript {
  dependencies {
    classpath(libs.android.gradle.plugin)
    classpath(libs.kotlinx.benchmark.gradle.plugin)
    classpath(libs.dokka.gradle.plugin)
    classpath(libs.kotlin.serialization.gradle.plugin)
    classpath(libs.kotlinx.binaryCompatibility.gradle.plugin)
    classpath(libs.kotlin.gradle.plugin)
    classpath(libs.google.ksp)
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
  id("dokka")
  id("dokka-version-archive")
}

subprojects {

  afterEvaluate {
    configurations.configureEach {
      // There could be transitive dependencies in tests with a lower version. This could cause
      // problems with a newer Kotlin version that we use.
      resolutionStrategy.force(libs.kotlin.reflect)
    }
  }
}

applyKtLint()

apply(from = rootProject.file(".buildscript/binary-validation.gradle"))

// Publish tasks use the output of Sign tasks, but don't actually declare a dependency upon it,
// which then causes execution optimizations to be disabled.  If this target project has Publish
// tasks, explicitly make them run after Sign.
subprojects {
  tasks.matching { it is AbstractPublishToMaven }
    .all { mustRunAfter(tasks.matching { it is Sign }) }
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
  description = "Generate dokka Html for the documentation site."
  group = "documentation"
  dependsOn(":dokkaHtmlMultiModule")

  // Copy the files instead of configuring a different output directory on the dokka task itself
  // since the default output directories disambiguate between different types of outputs, and our
  // custom directory doesn't.
  from(buildDir.resolve("dokka/htmlMultiModule/workflow"))
  into(buildDir.resolve("dokka/workflow"))
}
