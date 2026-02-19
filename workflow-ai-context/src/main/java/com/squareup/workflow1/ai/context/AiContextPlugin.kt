package com.squareup.workflow1.ai.context

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin that registers the `extractAiContext` task.
 *
 * Apply to any project that depends on workflow-kotlin artifacts and wants
 * AI context (AGENTS.md, skills) automatically extracted to the project root
 * for AI tools like Firebender, Cursor, and Claude.
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
    target.tasks.register("extractAiContext", AiContextExtractTask::class.java) { task ->
      task.group = "ai"
      task.description =
        "Extracts AI context (AGENTS.md, skills) from workflow-kotlin JARs on the classpath."
    }
  }
}
