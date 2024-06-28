import com.squareup.workflow1.buildsrc.shardConnectedCheckTasks
import org.jetbrains.dokka.gradle.AbstractDokkaLeafTask
import java.net.URL

buildscript {
  dependencies {
    classpath(libs.android.gradle.plugin)
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
  id("artifacts-check")
  id("dependency-guard")
  alias(libs.plugins.ktlint)
  id("com.autonomousapps.dependency-analysis") version "1.32.0"
}

shardConnectedCheckTasks(project)

subprojects {

  afterEvaluate {
    configurations.configureEach {
      // There could be transitive dependencies in tests with a lower version. This could cause
      // problems with a newer Kotlin version that we use.
      resolutionStrategy.force(libs.kotlin.reflect)
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
  tasks.withType<AbstractDokkaLeafTask> {

    // This is the displayed name for the module, like in the Html sidebar.
    //   artifact id: workflow-internal-testing-utils
    //          path: internal-testing-utils
    moduleName.set(
      provider {
        findProperty("POM_ARTIFACT_ID") as? String
          ?: project.path.removePrefix(":")
      }
    )

    dokkaSourceSets.configureEach {

      val dokkaSourceSet = this

      reportUndocumented.set(false)
      skipDeprecated.set(true)

      if (file("src/${dokkaSourceSet.name}").exists()) {

        val readmeFile = file("$projectDir/README.md")
        // If the module has a README, add it to the module's index
        if (readmeFile.exists()) {
          includes.from(readmeFile)
        }

        sourceLink {
          localDirectory.set(file("src/${dokkaSourceSet.name}"))

          val modulePath = projectDir.relativeTo(rootDir).path

          // URL showing where the source code can be accessed through the web browser
          remoteUrl.set(
            @Suppress("ktlint:standard:max-line-length")
            URL(
              "https://github.com/square/workflow-kotlin/blob/main/$modulePath/src/${dokkaSourceSet.name}"
            )
          )
          // Suffix which is used to append the line number to the URL. Use #L for GitHub
          remoteLineSuffix.set("#L")
        }
      }
      perPackageOption {
        // Will match all .internal packages and sub-packages, regardless of module.
        matchingRegex.set(""".*\.internal.*""")
        suppress.set(true)
      }
    }
  }
}

// Publish tasks use the output of Sign tasks, but don't actually declare a dependency upon it,
// which then causes execution optimizations to be disabled.  If this target project has Publish
// tasks, explicitly make them run after Sign.
subprojects {
  tasks.withType(AbstractPublishToMaven::class.java)
    .configureEach { mustRunAfter(tasks.matching { it is Sign }) }
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
