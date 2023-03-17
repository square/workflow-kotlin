package com.squareup.workflow1.builds

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost.Companion.S01
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.signing.Sign

interface PublishedExtension {
  fun Project.published(
    artifactId: String,
    name: String,
    description: String = name
  ) {
    plugins.apply("com.vanniktech.maven.publish.base")
    plugins.apply("org.jetbrains.dokka")
    plugins.apply("dependency-guard")

    configurePublish(
      artifactId = artifactId,
      name = name,
      description = description,
      groupId = "com.squareup.workflow1"
    )
  }
}

private fun Project.versionIsSnapshot(): Boolean {
  return (property("VERSION_NAME") as String).endsWith("-SNAPSHOT")
}

private fun Project.configurePublish(
  artifactId: String,
  name: String,
  description: String,
  groupId: String
) {
  version = property("VERSION_NAME") as String

  @Suppress("UnstableApiUsage")
  extensions.configure(MavenPublishBaseExtension::class.java) { extension ->

    extension.publishToMavenCentral(S01, automaticRelease = true)

    extension.signAllPublications()

    extension.coordinates(
      groupId = groupId,
      artifactId = artifactId,
      version = project.property("VERSION_NAME") as String
    )
    extension.pom {
      val mavenPom = this@pom

      mavenPom.description.set(description)
      mavenPom.name.set(name)

      mavenPom.url.set("https://www.github.com/square/workflow/")

      mavenPom.licenses {
        val licenseSpec = this@licenses

        licenseSpec.license {
          val license = this@license

          license.name.set("The Apache Software License, Version 2.0")
          license.url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
          license.distribution.set("repo")
        }
      }
      mavenPom.scm {
        val scm = this@scm

        scm.url.set("https://www.github.com/square/workflow/")
        scm.connection.set("scm:git:git://github.com/square/workflow.git")
        scm.developerConnection.set("scm:git:ssh://git@github.com/square/workflow.git")
      }
      mavenPom.developers {
        val developerSpec = this@developers

        developerSpec.developer {
          val developer = this@developer

          developer.id.set("square")
          developer.name.set("Square, Inc.")
          developer.url.set("https://github.com/square/")
        }
      }
    }

    pluginManager.withPlugin("com.android.library") {
      extension.configure(AndroidSingleVariantLibrary(sourcesJar = true, publishJavadocJar = true))
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      extension.configure(KotlinJvm(javadocJar = Dokka(taskName = "dokkaGfm"), sourcesJar = true))
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
      extension.configure(KotlinMultiplatform(javadocJar = Dokka(taskName = "dokkaGfm")))
    }
  }

  registerCoordinatesStringsCheckTask(groupId = groupId, artifactId = artifactId)
  registerSnapshotVersionCheckTask()

  tasks.withType(PublishToMavenRepository::class.java) { task ->
    task.notCompatibleWithConfigurationCache("See https://github.com/gradle/gradle/issues/13468")
  }
  tasks.withType(Jar::class.java) { task ->
    task.notCompatibleWithConfigurationCache("")
  }
  tasks.withType(Sign::class.java) { task ->
    task.notCompatibleWithConfigurationCache("")
    // skip signing for -SNAPSHOT publishing
    task.onlyIf { !versionIsSnapshot() }
  }
}

private fun Project.registerCoordinatesStringsCheckTask(
  groupId: String,
  artifactId: String
) {
  val checkTask = tasks.register("checkMavenCoordinatesStrings") { task ->

    task.group = "publishing"
    task.description = "checks that the project's maven group and artifact ID are valid for Maven"

    task.doLast {
      val allowedRegex = "^[A-Za-z0-9_\\-.]+$".toRegex()

      check(groupId.matches(allowedRegex)) {
        val actualString = when {
          groupId.isEmpty() -> "<<empty string>>"
          else -> groupId
        }
        "groupId ($actualString) is not a valid Maven identifier ($allowedRegex)."
      }

      check(artifactId.matches(allowedRegex)) {
        val actualString = when {
          artifactId.isEmpty() -> "<<empty string>>"
          else -> artifactId
        }
        "artifactId ($actualString) is not a valid Maven identifier ($allowedRegex)."
      }
    }
  }

  tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
    val task = this@named

    task.dependsOn(checkTask)
  }
}

private fun Project.registerSnapshotVersionCheckTask() {
  tasks.register("checkVersionIsSnapshot") { task ->

    task.group = "publishing"
    task.description = "ensures that the project version has a -SNAPSHOT suffix"
    val versionString = version as String
    task.doLast {
      val expected = "-SNAPSHOT"
      require(versionString.endsWith(expected)) {
        "The project's version name must be suffixed with `$expected` when checked in" +
          " to the main branch, but instead it's `$versionString`."
      }
    }
  }
  tasks.register("checkVersionIsNotSnapshot") { task ->

    task.group = "publishing"
    task.description = "ensures that the project version does not have a -SNAPSHOT suffix"
    val versionString = version as String
    task.doLast {
      require(!versionString.endsWith("-SNAPSHOT")) {
        "The project's version name cannot have a -SNAPSHOT suffix, but it was $versionString."
      }
    }
  }
}
