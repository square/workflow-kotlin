package com.squareup.workflow1.ai.context

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin that registers the `extractAiContext` task.
 *
 * Apply to any project that depends on workflow-kotlin artifacts and wants
 * AI context (AGENTS.md, skills) automatically extracted to the project root
 * for AI tools like Amp, Codex, Cursor, and Claude.
 *
 * Usage in build.gradle.kts:
 * ```
 * plugins {
 *   id("com.squareup.workflow1.ai-context")
 * }
 * ```
 */
public class AiContextPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val extension = target.extensions.create("aiContext", AiContextExtension::class.java)
    extension.outputDirectory.convention(target.layout.projectDirectory)
    extension.agentsFile.convention(extension.outputDirectory.file("AGENTS.md"))
    extension.tools.convention(listOf("amp"))
    extension.skillsDirectories.convention(emptyList())

    target.tasks.register("extractAiContext", AiContextExtractTask::class.java) { task ->
      task.group = "ai"
      task.description =
        "Extracts AI context (AGENTS.md, skills) from workflow-kotlin JARs on the classpath."
      task.outputDirectory.convention(extension.outputDirectory)
      task.agentsFile.convention(extension.agentsFile)
      task.tools.convention(extension.tools.map { it.joinToString(",") })
      task.skillsDirectories.convention(extension.skillsDirectories)
    }
  }
}
