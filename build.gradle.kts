import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

buildscript {
  dependencies {
    classpath(libs.android.gradle.plugin)
    classpath(libs.jmh.gradle.plugin)
    classpath(libs.dokka.gradle.plugin)
    classpath(libs.kotlin.serialization.gradle.plugin)
    classpath(libs.kotlinx.binaryCompatibility.gradle.plugin)
    classpath(libs.kotlin.gradle.plugin)
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

// See https://stackoverflow.com/questions/25324880/detect-ide-environment-with-gradle
val isRunningFromIde get() = project.properties["android.injected.invoked.from.ide"] == "true"

plugins {
  `binary-validation`
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

val foo by tasks.registering {
  doLast {

    val yaml = allprojects
      .associateWith { proj ->
        proj.configurations
          .flatMap { configuration -> configuration.dependencies }
          .filterIsInstance<ExternalModuleDependency>()
          .map { "${it.module}:${it.version}" }
          .distinct()
          .sorted()
      }
      .toList()
      .sortedBy { it.first.path }
      .joinToString("\n") { (project, deps) ->
        "${project.path}\n" + deps.joinToString("\n") { "\t$it" }
      }

    println(yaml)

    // val diff = deps.minus(yaml)
    //
    // println(diff.joinToString("\n"))

  }
}

val deps = setOf(
  "androidx.activity:activity-compose:1.3.1",
  "androidx.activity:activity-ktx:1.3.0",
  "androidx.activity:activity:1.3.0",
  "androidx.appcompat:appcompat:1.3.1",
  "androidx.compose.compiler:compiler:1.1.0-rc02",
  "androidx.compose.foundation:foundation:1.1.0-rc01",
  "androidx.compose.material:material:1.1.0-rc01",
  "androidx.compose.ui:ui-test-junit4:1.0.1",
  "androidx.compose.ui:ui-tooling:1.1.0-rc01",
  "androidx.compose.ui:ui:1.1.0-rc01",
  "androidx.constraintlayout:constraintlayout:2.1.2",
  "androidx.databinding:viewbinding:4.2.1",
  "androidx.databinding:viewbinding:7.0.0",
  "androidx.fragment:fragment-ktx:1.3.6",
  "androidx.fragment:fragment:1.3.6",
  "androidx.gridlayout:gridlayout:1.0.0",
  "androidx.lifecycle:lifecycle-runtime-ktx:2.4.0",
  "androidx.lifecycle:lifecycle-runtime-testing:2.4.0",
  "androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.0",
  "androidx.lifecycle:lifecycle-viewmodel-savedstate:1.1.0",
  "androidx.lifecycle:lifecycle-viewmodel:2.4.0",
  "androidx.recyclerview:recyclerview:1.2.1",
  "androidx.savedstate:savedstate:1.1.0",
  "androidx.test.espresso:espresso-core:3.3.0",
  "androidx.test.espresso:espresso-idling-resource:3.3.0",
  "androidx.test.espresso:espresso-intents:3.3.0",
  "androidx.test.ext:junit:1.1.3",
  "androidx.test.ext:truth:1.4.0",
  "androidx.test.uiautomator:uiautomator:2.2.0",
  "androidx.test:core:1.3.0",
  "androidx.test:runner:1.4.0",
  "androidx.transition:transition:1.4.1",
  "com.android.tools.lint:lint-gradle:30.0.0",
  "com.android.tools:desugar_jdk_libs:1.1.5",
  "com.google.android.material:material:1.3.0",
  "com.google.truth:truth:1.1.3",
  "com.googlecode.lanterna:lanterna:3.1.1",
  "com.jakewharton.timber:timber:4.7.1",
  "com.pinterest:ktlint:0.42.1",
  "com.squareup.cycler:cycler:0.1.9",
  "com.squareup.leakcanary:leakcanary-android-instrumentation:2.8.1",
  "com.squareup.leakcanary:leakcanary-android:2.8.1",
  "com.squareup.moshi:moshi-adapters:1.13.0",
  "com.squareup.moshi:moshi-kotlin-codegen:1.13.0",
  "com.squareup.moshi:moshi:1.13.0",
  "com.squareup.okio:okio:2.10.0",
  "com.squareup.radiography:radiography:2.4.0",
  "com.squareup:seismic:1.0.2",
  "io.mockk:mockk:1.11.0",
  "io.reactivex.rxjava2:rxandroid:2.1.1",
  "io.reactivex.rxjava2:rxjava:2.2.21",
  "junit:junit:4.13.2",
  "org.hamcrest:hamcrest-core:2.2",
  "org.jacoco:org.jacoco.ant:0.8.3",
  "org.jetbrains.dokka:all-modules-page-plugin:1.5.31",
  "org.jetbrains.dokka:dokka-base:1.5.31",
  "org.jetbrains.dokka:gfm-plugin:1.5.31",
  "org.jetbrains.dokka:gfm-template-processing-plugin:1.5.31",
  "org.jetbrains.dokka:javadoc-plugin:1.5.31",
  "org.jetbrains.dokka:jekyll-plugin:1.5.31",
  "org.jetbrains.dokka:jekyll-template-processing-plugin:1.5.31",
  "org.jetbrains.kotlin:kotlin-annotation-processing-gradle:1.6.10",
  "org.jetbrains.kotlin:kotlin-parcelize-compiler:1.6.10",
  "org.jetbrains.kotlin:kotlin-parcelize-runtime:1.6.10",
  "org.jetbrains.kotlin:kotlin-reflect:1.6.10",
  "org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:1.6.10",
  "org.jetbrains.kotlin:kotlin-serialization:1.6.10",
  "org.jetbrains.kotlin:kotlin-stdlib-jdk7:null",
  "org.jetbrains.kotlin:kotlin-stdlib-jdk8:null",
  "org.jetbrains.kotlin:kotlin-stdlib:1.6.10",
  "org.jetbrains.kotlin:kotlin-stdlib:null",
  "org.jetbrains.kotlin:kotlin-test-junit:null",
  "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.1",
  "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1",
  "org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.5.1",
  "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.1",
  "org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2",
  "org.jetbrains:annotations:19.0.0",
  "org.mockito.kotlin:mockito-kotlin:3.2.0",
  "org.openjdk.jmh:jmh-core:1.32",
  "org.openjdk.jmh:jmh-generator-annprocess:1.32",
  "org.robolectric:robolectric:4.5.1"
)
