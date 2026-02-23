package com.squareup.workflow1.buildsrc

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Sync
import org.gradle.language.jvm.tasks.ProcessResources

class PublishingConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {

    target.plugins.apply("dokka")
    target.plugins.apply("com.vanniktech.maven.publish.base")
    // track all runtime classpath dependencies for anything we ship
    target.plugins.apply("dependency-guard")

    target.version = target.property("VERSION_NAME") as String

    // This should not be necessary, required by a bug introduced with
    // Vanniktech 0.22.0
    // https://workflow-community.slack.com/archives/GT9FD1XKL/p1667330008947479?thread_ts=1667329022.412179&cid=GT9FD1XKL
    target.group = "com.squareup.workflow1"

    target.tasks.register("checkVersionIsSnapshot") {
      it.doLast {
        val expected = "-SNAPSHOT"
        require((target.version as String).endsWith(expected)) {
          "The project's version name must be suffixed with `$expected` when checked in" +
            " to the main branch, but instead it's `${target.version}`."
        }
      }
    }

    target.tasks.register("checkVersionIsNotSnapshot") { task ->
      task.group = "publishing"
      task.description = "ensures that the project version does not have a -SNAPSHOT suffix"
      val versionString = target.version as String
      task.doLast {
        require(!versionString.endsWith("-SNAPSHOT")) {
          "The project's version name cannot have a -SNAPSHOT suffix, but it was $versionString."
        }
      }
    }

    target.extensions.configure(MavenPublishBaseExtension::class.java) { basePluginExtension ->
      basePluginExtension.publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
      // Will only apply to non snapshot builds.
      basePluginExtension.signAllPublications()
      // import all settings from root project and project-specific gradle.properties files
      basePluginExtension.pomFromGradleProperties()

      val artifactId = target.property("POM_ARTIFACT_ID") as String
      val pomDescription = target.property("POM_NAME") as String

      target.pluginManager.withPlugin("com.android.library") {
        basePluginExtension.configure(AndroidSingleVariantLibrary(sourcesJar = true))
        target.setPublicationProperties(pomDescription, artifactId)
      }
      target.pluginManager.withPlugin("java-gradle-plugin") {
        basePluginExtension.configure(
          GradlePlugin(
            javadocJar = JavadocJar.Dokka(taskName = "dokkaGeneratePublicationHtml"),
            sourcesJar = true
          )
        )
        target.setPublicationProperties(pomDescription, artifactId)
      }
      target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        if (!target.pluginManager.hasPlugin("java-gradle-plugin")) {
          basePluginExtension.configure(
            KotlinJvm(
              javadocJar = JavadocJar.Dokka(taskName = "dokkaGeneratePublicationHtml"),
              sourcesJar = true
            )
          )
          target.setPublicationProperties(pomDescription, artifactId)
        }
      }
      target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        basePluginExtension.configure(
          KotlinMultiplatform(javadocJar = JavadocJar.Dokka(taskName = "dokkaGeneratePublicationHtml"))
        )
        // don't set the artifactId for KMP, because this is handled by the KMP plugin itself
        target.setPublicationProperties(pomDescription, artifactIdOrNull = null)
      }
    }

    val syncAiRulesResources =
      target.tasks.register("syncAiRulesResources", Sync::class.java) { sync ->
        val outputDir = target.layout.buildDirectory.dir("generated/ai-rules-resources")
        sync.into(outputDir)

        sync.from(target.layout.projectDirectory.dir(".agents")) {
          it.include("skills/**/SKILL.md")
          it.into("META-INF/com.squareup.workflow1")
        }

        val moduleAgents = target.layout.projectDirectory.file("AGENTS.md").asFile
        if (moduleAgents.exists()) {
          sync.from(moduleAgents) {
            it.into("META-INF/com.squareup.workflow1")
          }
        }

        if (target.name == "workflow-core") {
          val rootAgents = target.rootProject.layout.projectDirectory.file("AGENTS.md").asFile
          if (rootAgents.exists()) {
            sync.from(rootAgents) {
              it.into("META-INF/com.squareup.workflow1")
            }
          }
          // Also bundle root-level skills (e.g., extracting-ai-context)
          sync.from(target.rootProject.layout.projectDirectory.dir(".agents")) {
            it.include("skills/**/SKILL.md")
            it.into("META-INF/com.squareup.workflow1")
          }
        }
      }

    target.tasks.withType(ProcessResources::class.java).configureEach { processResources ->
      processResources.dependsOn(syncAiRulesResources)
      processResources.from(syncAiRulesResources.map { it.destinationDir })
    }

    target.tasks.withType(PublishToMavenRepository::class.java) {
      it.notCompatibleWithConfigurationCache("See https://github.com/gradle/gradle/issues/13468")
      it.dependsOn(syncAiRulesResources)
    }
  }

  private fun Project.setPublicationProperties(
    pomDescription: String,
    artifactIdOrNull: String?
  ) {
    // This has to be inside an `afterEvaluate { }` because these publications are created lazily,
    // using the project's `name`, which is just the directory name instead of the actual artifact id.
    // We can't set `name` because it's immutable, so we have to wait until the publication is
    // created, then overwrite the incorrect value.
    afterEvaluate {
      extensions
        .configure(PublishingExtension::class.java) { publishingExtension ->
          publishingExtension.publications
            .filterIsInstance<MavenPublication>()
            // Skip Gradle plugin marker publications — their coordinates are
            // managed by the java-gradle-plugin and must not be overridden.
            .filterNot { it.name.endsWith("PluginMarkerMaven") }
            .forEach { publication ->

              if (artifactIdOrNull != null) {
                publication.artifactId = artifactIdOrNull
              }
              publication.pom.description.set(pomDescription)

              // Note that we're setting the `groupId` of this specific publication,
              // and not `Project.group`.  By default, `Project.group` is a project's parent,
              // so for example the group of `:workflow-ui:compose` is `workflow-ui`.  If we set every
              // project's `group` to the group id, then we use the natural disambiguation of unique
              // paths.  For instance, projects with paths of `:lib1:core` and `:lib2:core`
              // and a group of `com.example` would both have the coordinates of `com.example:core`.
              publication.groupId = project.property("GROUP") as String
            }
        }
    }
  }
}
