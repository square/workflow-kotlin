package com.squareup.workflow1.buildsrc.artifacts

import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Evaluates all published artifacts in the project and writes the results to `/artifacts.json`
 */
open class ArtifactsDumpTask @Inject constructor(
  projectLayout: ProjectLayout
) : ArtifactsTask(projectLayout) {

  init {
    description = "Parses the Maven artifact parameters for all modules " +
      "and writes them to artifacts.json"
    group = "other"
  }

  @TaskAction
  fun run() {

    val json = moshiAdapter.indent("  ").toJson(currentList)

    reportFile.asFile.writeText(json)
  }
}
