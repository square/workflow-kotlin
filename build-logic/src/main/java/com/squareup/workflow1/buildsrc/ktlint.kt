package com.squareup.workflow1.buildsrc

import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

/**
 * Applies Kotlinter settings to any project, with additional tasks for the root project.
 *
 * @since 0.10.0
 */
fun Project.applyKtLint() {
  pluginManager.apply("org.jmailen.kotlinter")

  tasks.withType(LintTask::class.java) {
    ignoreFailures.set(false)
    mustRunAfter(tasks.withType(FormatTask::class.java))
  }

  if (project == rootProject) {
    tasks.register("lintKotlin")
    tasks.register("formatKotlin")
    afterEvaluate {
      addGradleScriptTasks(tasks, taskNameQualifier = "")
    }
  }

  // dummy ktlint-gradle plugin task names which just delegate to the Kotlinter ones
  tasks.register("ktlintCheck") {
    dependsOn(tasks.withType(LintTask::class.java))
  }
  tasks.register("ktlintFormat") {
    dependsOn(tasks.withType(FormatTask::class.java))
  }
}

// Add check/format tasks to each root project (including child root projects) which target every
// `build.gradle.kts` and `settings.gradle.kts` file within that project group.
private fun Project.addGradleScriptTasks(
  taskContainer: TaskContainer,
  taskNameQualifier: String = ""
) {
  val includedProjectScriptFiles = allprojects
    .flatMap { included ->
      listOfNotNull(
        included.buildFile,
        included.file("settings.gradle.kts").takeIf { it.exists() }
      )
    }

  val lintKotlinBuildLogic = taskContainer.register(
    "lintKotlin${taskNameQualifier}BuildScripts",
    LintTask::class.java
  ) {
    group = "Formatting"
    description = "Runs lint on the build and settings files"
    source(includedProjectScriptFiles)
  }

  tasks.named("lintKotlin") {
    dependsOn(lintKotlinBuildLogic)
  }

  val formatKotlinBuildLogic = taskContainer.register(
    "formatKotlin${taskNameQualifier}BuildScripts",
    FormatTask::class.java
  ) {
    group = "Formatting"
    description = "Formats the build and settings files"
    source(includedProjectScriptFiles)
  }
  tasks.named("formatKotlin") {
    dependsOn(formatKotlinBuildLogic)
  }
}
