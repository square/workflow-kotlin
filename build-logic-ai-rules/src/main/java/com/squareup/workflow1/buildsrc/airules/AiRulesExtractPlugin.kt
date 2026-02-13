package com.squareup.workflow1.buildsrc.airules

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Convention plugin that registers the `extractAiRules` task.
 *
 * Apply to any project that depends on workflow-kotlin artifacts and wants
 * AI rules/skills automatically extracted to the project root for AI tools
 * like Firebender, Cursor, and Claude.
 *
 * Usage in build.gradle.kts:
 * ```
 * plugins {
 *   id("ai-rules-extract")
 * }
 * ```
 */
public class AiRulesExtractPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.tasks.register("extractAiRules", AiRulesExtractTask::class.java) { task ->
      task.group = "ai"
      task.description =
        "Extracts AI rules and skills from workflow-kotlin JARs on the classpath " +
          "into .firebender/, .cursor/, and .claude/ directories."
    }
  }
}
