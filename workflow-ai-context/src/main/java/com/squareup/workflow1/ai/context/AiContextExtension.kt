package com.squareup.workflow1.ai.context

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty

/**
 * Configuration for the workflow-kotlin AI context extraction plugin.
 */
public abstract class AiContextExtension {

  /**
   * Base directory used for relative skill output paths.
   *
   * Defaults to the project directory where the plugin is applied.
   */
  public abstract val outputDirectory: DirectoryProperty

  /**
   * File where extracted `AGENTS.md` guidance is merged.
   *
   * Defaults to `AGENTS.md` in [outputDirectory].
   */
  public abstract val agentsFile: RegularFileProperty

  /**
   * Agent names used to derive standard skills directories.
   *
   * Defaults to `amp`, which writes to `.agents/skills`.
   */
  public abstract val tools: ListProperty<String>

  /**
   * Explicit skills directories to write to.
   *
   * Relative paths are resolved from [outputDirectory]. When set, this overrides [tools].
   */
  public abstract val skillsDirectories: ListProperty<String>
}
