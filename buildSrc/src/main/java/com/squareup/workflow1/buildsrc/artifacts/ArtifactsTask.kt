package com.squareup.workflow1.buildsrc.artifacts

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile

abstract class ArtifactsTask(
  private val projectLayout: ProjectLayout
) : DefaultTask() {

  /**
   * This file contains all definitions for published artifacts.
   *
   * It's located at the root of the project, assuming that the task is run from the root project.
   */
  @get:OutputFile
  protected val reportFile: RegularFile by lazy {
    projectLayout.projectDirectory.file("artifacts.json")
  }

  /**
   * All artifacts as they are defined in the project right now.
   *
   * This is a lazy delegate because it's accessing [project], and Gradle's configuration caching
   * doesn't allow direct references to `project` in task properties or inside task actions.
   * Somehow, it doesn't complain about this even though it's definitely accessed at runtime.
   */
  @get:Internal
  protected val currentList by lazy { project.createArtifactList() }

  @get:Internal
  protected val moshiAdapter: JsonAdapter<List<ArtifactConfig>> by lazy {

    val type = Types.newParameterizedType(
      List::class.java,
      ArtifactConfig::class.java
    )

    Moshi.Builder()
      .build()
      .adapter(type)
  }

  private fun Project.createArtifactList(): List<ArtifactConfig> {

    val map = subprojects
      .flatMap { sub ->
        sub.extensions.findByType(PublishingExtension::class.java)
          ?.publications
          ?.filterIsInstance<MavenPublication>()
          .orEmpty()
          .map { publication -> sub to publication }
      }
      .mapNotNull { (sub, publication) ->

        val group: String? = publication.groupId
        val artifactId: String? = publication.artifactId
        val pomDescription: String? = publication.pom.name.orNull
        val packaging: String? = publication.pom.packaging

        listOfNotNull(group, artifactId, pomDescription, packaging)
          .also { allProperties ->

            require(allProperties.isEmpty() || allProperties.size == 4) {
              "expected all properties to be null or none to be null for project `${sub.path}, " +
                "but got:\n" +
                "group : $group\n" +
                "artifactId : $artifactId\n" +
                "pom description : $pomDescription\n" +
                "packaging : $packaging"
            }
          }
          .takeIf { it.size == 4 }
          ?.let { (group, artifactId, pomDescription, packaging) ->

            val javaVersion = sub.extensions.getByType(JavaPluginExtension::class.java)
              .sourceCompatibility
              .toString()

            ArtifactConfig(
              gradlePath = sub.path,
              group = group,
              artifactId = artifactId,
              description = pomDescription,
              packaging = packaging,
              javaVersion = javaVersion,
              publicationName = publication.name
            )
          }
      }

    return map
  }
}
