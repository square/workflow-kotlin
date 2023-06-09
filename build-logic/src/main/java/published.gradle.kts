@file:Suppress("UnstableApiUsage")

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost

plugins {
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  // track all runtime classpath dependencies for anything we ship
  id("dependency-guard")
}

version = project.property("VERSION_NAME") as String

// This should not be necessary, required by a bug introduced with
// Vanniktech 0.22.0
// https://workflow-community.slack.com/archives/GT9FD1XKL/p1667330008947479?thread_ts=1667329022.412179&cid=GT9FD1XKL
project.group = "com.squareup.workflow1"

tasks.register("checkVersionIsSnapshot") {
  doLast {
    val expected = "-SNAPSHOT"
    require((version as String).endsWith(expected)) {
      "The project's version name must be suffixed with `$expected` when checked in" +
        " to the main branch, but instead it's `$version`."
    }
  }
}

configure<MavenPublishBaseExtension> {
  publishToMavenCentral(SonatypeHost.S01)
  // Will only apply to non snapshot builds.
  signAllPublications()
  // import all settings from root project and project-specific gradle.properties files
  pomFromGradleProperties()

  val artifactId = project.property("POM_ARTIFACT_ID") as String
  val pomDescription = project.property("POM_NAME") as String

  pluginManager.withPlugin("com.android.library") {
    configure(AndroidSingleVariantLibrary(sourcesJar = true))
    setPublicationProperties(pomDescription, artifactId)
  }
  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    configure(KotlinJvm(javadocJar = Dokka(taskName = "dokkaGfm"), sourcesJar = true))
    setPublicationProperties(pomDescription, artifactId)
  }
  pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    configure(KotlinMultiplatform(javadocJar = Dokka(taskName = "dokkaGfm")))
    // don't set the artifactId for KMP, because this is handled by the KMP plugin itself
    setPublicationProperties(pomDescription, artifactIdOrNull = null)
  }
}

fun setPublicationProperties(
  pomDescription: String,
  artifactIdOrNull: String?
) {
  // This has to be inside an `afterEvaluate { }` because these publications are created lazily,
  // using the project's `name`, which is just the directory name instead of the actual artifact id.
  // We can't set `name` because it's immutable, so we have to wait until the publication is
  // created, then overwrite the incorrect value.
  afterEvaluate {
    configure<PublishingExtension> {
      publications.filterIsInstance<MavenPublication>()
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

tasks.withType(PublishToMavenRepository::class.java).configureEach {
  notCompatibleWithConfigurationCache("See https://github.com/gradle/gradle/issues/13468")
}
