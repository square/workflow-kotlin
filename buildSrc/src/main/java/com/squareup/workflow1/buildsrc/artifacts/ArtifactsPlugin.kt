package com.squareup.workflow1.buildsrc.artifacts

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.language.base.plugins.LifecycleBasePlugin

abstract class ArtifactsPlugin : Plugin<Project> {

  override fun apply(target: Project) {

    target.checkProjectIsRoot()

    target.tasks.register("artifactsDump", ArtifactsDumpTask::class.java)
    val artifactsCheck = target.tasks.register("artifactsCheck", ArtifactsCheckTask::class.java)

    target.tasks
      .matching { it.name == LifecycleBasePlugin.CHECK_TASK_NAME }
      .configureEach { task -> task.dependsOn(artifactsCheck) }

    // prevent publishing without checking all artifacts first
    target.allprojects {
      target.tasks.withType(AbstractPublishToMaven::class.java) {
        it.dependsOn(artifactsCheck)
      }
    }
  }
}

fun Project.checkProjectIsRoot(
  message: () -> Any = { "Only apply this plugin to the project root." }
) {
  check(this == rootProject, message)
}
