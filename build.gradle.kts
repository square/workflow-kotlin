import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

buildscript {
  dependencies {
    classpath(Dependencies.android_gradle_plugin)
    classpath(Dependencies.dokka)
    classpath(Dependencies.Jmh.gradlePlugin)
    classpath(Dependencies.Kotlin.binaryCompatibilityValidatorPlugin)
    classpath(Dependencies.Kotlin.gradlePlugin)
    classpath(Dependencies.Kotlin.Serialization.gradlePlugin)
    classpath(Dependencies.ktlint)
    classpath(Dependencies.mavenPublish)
  }

  repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
    // For binary compatibility validator.
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
  }
}

// See https://stackoverflow.com/questions/25324880/detect-ide-environment-with-gradle
val isRunningFromIde get() = project.properties["android.injected.invoked.from.ide"] == "true"

subprojects {
  repositories {
    google()
    mavenCentral()
    jcenter()
  }

  apply(plugin = "org.jlleitschuh.gradle.ktlint")
  afterEvaluate {
    configurations.configureEach {
      // There could be transitive dependencies in tests with a lower version. This could cause
      // problems with a newer Kotlin version that we use.
      resolutionStrategy.force(Dependencies.Kotlin.reflect)
    }
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions {
      // Allow warnings when running from IDE, makes it easier to experiment.
      if (!isRunningFromIde) {
        allWarningsAsErrors = true
      }

      jvmTarget = "1.8"

      // Don't panic, all this does is allow us to use the @OptIn meta-annotation.
      // to define our own experiments.
      freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
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

        // We had to disable the indent and parameter-list-wrapping rules, because they lead to
        // false positives even in the most recent KtLint version. We created tickets:
        //
        // https://github.com/pinterest/ktlint/issues/963
        // https://github.com/pinterest/ktlint/issues/964
        // https://github.com/pinterest/ktlint/issues/965
        //
        // We can't revert the KtLint version, because they only work with Kotlin 1.3 and would
        // block Kotlin 1.4. We rather have a newer Kotlin version than a proper indent. The
        // indent rule needs to be disabled globally due to another bug:
        // https://github.com/pinterest/ktlint/issues/967
        "indent",
        "parameter-list-wrapping"
      )
    )
  }
}

apply(from = rootProject.file(".buildscript/binary-validation.gradle"))

// Require explicit public modifiers and types for actual library modules, not samples.
allprojects.filterNot { it.path.startsWith(":samples") }
  .forEach {
    it.tasks.withType<KotlinCompile>().configureEach {
      // Tests and benchmarks aren't part of the public API, don't turn explicit API mode on for
      // them.
      if (!name.contains("test", ignoreCase = true) &&
        !name.contains("jmh", ignoreCase = true)
      ) {
        kotlinOptions {
          // TODO this should be moved to `kotlin { explicitApi() }` once that's working for android
          //  projects, see https://youtrack.jetbrains.com/issue/KT-37652.
          @Suppress("SuspiciousCollectionReassignment")
          freeCompilerArgs += "-Xexplicit-api=strict"

          // Make sure our module names don't conflict with those from pre-workflow1
          // releases, so that old and new META-INF/ entries don't stomp each other.
          // (This is only an issue for apps that are still migrating from workflow to
          // workflow1, and so need to import two versions of the library.)
          // https://blog.jetbrains.com/kotlin/2015/09/kotlin-m13-is-out/
          moduleName = "wf1-${it.name}"
        }
      }
    }
  }

// This plugin needs to be applied to the root projects for the dokkaGfmCollector task we use to
// generate the documentation site.
apply(plugin = "org.jetbrains.dokka")
repositories {
  mavenCentral()
}

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
