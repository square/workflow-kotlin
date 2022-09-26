package com.squareup.workflow1.buildsrc.dokka

import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.squareup.workflow1.VERSION_NAME
import com.squareup.workflow1.library
import com.squareup.workflow1.libsCatalog
import com.vanniktech.maven.publish.tasks.JavadocJar
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.dokka.gradle.AbstractDokkaLeafTask
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaTaskPartial
import org.jetbrains.dokka.versioning.VersioningConfiguration
import org.jetbrains.dokka.versioning.VersioningPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask
import java.net.URL

abstract class DokkaConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("org.jetbrains.dokka")

    target.tasks.withType(AbstractDokkaLeafTask::class.java).configureEach {
      // Dokka doesn't support configuration caching
      notCompatibleWithConfigurationCache("Dokka doesn't support configuration caching")

      // Dokka uses their outputs but doesn't explicitly depend upon them.
      mustRunAfter(target.tasks.withType(KotlinCompile::class.java))
      mustRunAfter(target.tasks.withType(LintTask::class.java))
      mustRunAfter(target.tasks.withType(FormatTask::class.java))

      // This is the displayed name for the module, like in the Html sidebar.
      //   artifact id: workflow-internal-testing-utils
      //          path: internal-testing-utils
      moduleName.set(
        target.provider {
          target.findProperty("POM_ARTIFACT_ID") as? String
            ?: project.path.removePrefix(":")
        }
      )

      if (target != target.rootProject && target.file("src/main").exists()) {
        configureSourceSets(target)
      }
    }

    target.dependencies.add(
      "dokkaPlugin",
      target.libsCatalog.library("dokka-versioning")
    )

    val dokkaArchiveBuildDir = target.rootDir.resolve("build/tmp/dokka-archive")

    val versionName = target.VERSION_NAME

    fun AbstractDokkaTask.configureVersioning() {
      pluginConfiguration<VersioningPlugin, VersioningConfiguration> {
        version = versionName
        olderVersionsDir = dokkaArchiveBuildDir
        renderVersionsNavigationOnAllPages = true
      }
    }

    // DO NOT JUST CONFIGURE `AbstractDokkaTask`
    // This will bundle the full dokka archive (all versions) into the javadoc.jar for every single
    // module, which currently adds about 8MB per version in the archive. Set up versioning for the
    // Multi-Module tasks ONLY. (DokkaTaskPartial is part of the multi-module tasks).
    target.tasks.withType(DokkaTaskPartial::class.java).configureEach { configureVersioning() }
    target.tasks.withType(DokkaMultiModuleTask::class.java).configureEach { configureVersioning() }

    target.plugins.withType(MavenPublishPlugin::class.java).configureEach {
      val checkJavadocJarIsNotVersioned = target.tasks
        .register("checkJavadocJarIsNotVersioned") {
          description =
            "Ensures that generated javadoc.jar artifacts don't include old Dokka versions"
          group = "dokka versioning"

          val javadocTasks = target.tasks.withType(JavadocJar::class.java)
          dependsOn(javadocTasks)

          inputs.files(javadocTasks.map { it.outputs })

          val zipTrees = javadocTasks.map { target.zipTree(it.archiveFile) }

          doLast {
            val jsonReg = """older\/([^/]+)\/version\.json""".toRegex()

            val versions = zipTrees.flatMap { tree ->
              tree
                .filter { it.path.startsWith("older/") }
                .filter { it.isFile }
                .mapNotNull { jsonReg.find(it.path)?.groupValues?.get(1) }
            }

            if (versions.isNotEmpty()) {
              throw GradleException("Found old Dokka versions in javadoc.jar: $versions")
            }
          }
        }

      target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME)
        .dependsOn(checkJavadocJarIsNotVersioned)
    }
  }

  private fun AbstractDokkaLeafTask.configureSourceSets(target: Project) {
    dokkaSourceSets.named("main") {
      val dokkaSourceSet = this

      reportUndocumented.set(false)
      skipDeprecated.set(true)

      val readmeFile = target.file("README.md")

      if (readmeFile.exists()) {
        includes.from(readmeFile)
      }

      sourceLink {
        localDirectory.set(target.file("src/${dokkaSourceSet.name}"))

        val modulePath = target.projectDir.relativeTo(target.rootDir).path

        // URL showing where the source code can be accessed through the web browser
        remoteUrl.set(
          URL(
            "https://github.com/square/workflow-kotlin/blob/main/$modulePath/src/${dokkaSourceSet.name}"
          )
        )
        // Suffix which is used to append the line number to the URL. Use #L for GitHub
        remoteLineSuffix.set("#L")
      }

      perPackageOption {
        // Will match all .internal packages and sub-packages, regardless of module.
        matchingRegex.set(""".*\.internal.*""")
        suppress.set(true)
      }
    }
  }
}
