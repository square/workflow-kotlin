package com.squareup.workflow1.ai.context

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.util.jar.JarFile

/**
 * Gradle task that discovers runtime classpath archives and extracts AI context
 * (AGENTS.md, skills) from workflow-kotlin JARs.
 *
 * Use `--preview` to see what would be extracted without writing files.
 * For interactive prompting, use `scripts/extract-ai-context.sh` which wraps
 * this task with a shell-based confirmation prompt.
 */
public abstract class AiContextExtractTask : DefaultTask() {

  @get:Input
  @get:Option(
    option = "preview",
    description = "Print what would be extracted without writing files"
  )
  public abstract val preview: Property<Boolean>

  @get:Input
  @get:Option(
    option = "tools",
    description = "Comma-separated agent names (default: amp)"
  )
  public abstract val tools: Property<String>

  init {
    preview.convention(false)
    tools.convention("amp")
  }

  @TaskAction
  public fun extract() {
    val archiveFiles = collectClasspathArchives()
    logger.lifecycle(
      "Discovered ${archiveFiles.size} classpath archives to scan for AI context..."
    )

    if (archiveFiles.isEmpty()) {
      logger.lifecycle("No classpath archives found. Nothing to extract.")
      return
    }

    val toolNames = tools.get().split(",").map { it.trim().lowercase() }
    val toolDirs = toolNames.map { resolveSkillsDir(it) }.distinct()
    val outputDir = project.projectDir

    // Scan JARs for AI context entries
    val agentsContent = mutableListOf<String>()
    val skills = mutableListOf<Pair<String, ByteArray>>() // relativePath -> content

    for (jar in archiveFiles) {
      try {
        val (agents, jarSkills) = scanJar(jar)
        agentsContent.addAll(agents)
        skills.addAll(jarSkills)
      } catch (_: Exception) {
        // Skip non-JAR files or corrupt archives
      }
    }

    if (agentsContent.isEmpty() && skills.isEmpty()) {
      logger.lifecycle("No AI context found in classpath JARs.")
      return
    }

    // Determine AGENTS.md action
    val agentsFile = File(outputDir, "AGENTS.md")
    val agentsAction = when {
      agentsContent.isEmpty() -> null
      !agentsFile.exists() -> "Will create new file"
      agentsFile.readText().let {
        it.contains(AGENTS_INJECTION_START) && it.contains(AGENTS_INJECTION_END)
      } -> "Will update existing injection block"
      else -> "Will append to existing file"
    }

    // Preview: print report and return
    if (preview.get()) {
      logger.lifecycle("")
      logger.lifecycle("Found AI context from workflow-kotlin in classpath JARs.")
      logger.lifecycle("This will:")
      logger.lifecycle("")
      if (agentsAction != null) {
        val verb = when (agentsAction) {
          "Will create new file" -> "Create"
          "Will update existing injection block" -> "Update the injection block in"
          else -> "Append workflow-kotlin context to"
        }
        logger.lifecycle("  - $verb AGENTS.md with workflow-kotlin guidance")
      }
      if (skills.isNotEmpty()) {
        val skillNames = skills.map { it.first.substringBefore("/") }.distinct()
        logger.lifecycle(
          "  - Add ${skillNames.size} skills to ${toolDirs.joinToString(", ")} directories:"
        )
        for (skill in skillNames) {
          logger.lifecycle("      $skill")
        }
      }
      return
    }

    // Write AGENTS.md
    if (agentsContent.isNotEmpty()) {
      val existing = if (agentsFile.exists()) agentsFile.readText() else ""
      val newContent = mergeAgentsMd(existing, agentsContent)
      agentsFile.parentFile.mkdirs()
      agentsFile.writeText(newContent)
      logger.lifecycle("  Updated: AGENTS.md")
    }

    // Write skills
    var skillsCount = 0
    if (skills.isNotEmpty()) {
      for ((relativePath, content) in skills) {
        for (toolDir in toolDirs) {
          val targetFile = File(outputDir, "$toolDir/$relativePath")
          targetFile.parentFile.mkdirs()
          targetFile.writeBytes(content)
        }
        skillsCount++
        logger.lifecycle("  Extracted: skills/$relativePath")
      }
    }

    // Summary
    logger.lifecycle("")
    val agentsLabel = if (agentsContent.isNotEmpty()) "updated AGENTS.md" else ""
    val skillsLabel = if (skillsCount > 0) "$skillsCount skills" else ""
    val parts = listOf(agentsLabel, skillsLabel).filter { it.isNotEmpty() }
    if (parts.isNotEmpty()) {
      logger.lifecycle("Done! Extracted ${parts.joinToString(" and ")}.")
    } else {
      logger.lifecycle("No files were written.")
    }
  }

  /**
   * Finds all JAR/AAR files across resolved classpath configurations of all subprojects.
   * Uses lenient resolution to handle partial failures gracefully.
   */
  private fun collectClasspathArchives(): List<File> {
    return project.allprojects
      .flatMap { proj ->
        val matchingConfigs = proj.configurations
          .filter { config ->
            config.isCanBeResolved &&
              (
                config.name.endsWith("RuntimeClasspath", ignoreCase = true) ||
                  config.name.endsWith("CompileClasspath", ignoreCase = true)
                )
          }
        matchingConfigs.flatMap { config ->
          try {
            val resolved = config.incoming.artifactView { view ->
              view.lenient(true)
            }.files.files
            resolved.filter { it.extension == "jar" || it.extension == "aar" }
          } catch (e: Exception) {
            logger.debug("Could not resolve ${proj.name}/${config.name}: ${e.message}")
            emptyList()
          }
        }
      }
      .distinct()
  }

  internal companion object {
    internal const val PREFIX = "META-INF/com.squareup.workflow1/"
    internal const val SKILLS_PREFIX = "${PREFIX}skills/"
    internal const val AGENTS_FILE = "${PREFIX}AGENTS.md"
    internal const val AGENTS_INJECTION_SLUG = "workflow-kotlin-AGENTS-injection"
    internal const val AGENTS_INJECTION_START = "<!-- $AGENTS_INJECTION_SLUG:START -->"
    internal const val AGENTS_INJECTION_END = "<!-- $AGENTS_INJECTION_SLUG:END -->"

    /**
     * Standard agent-to-directory mappings following the Agent Skills specification.
     * See https://agentskills.io and https://github.com/vercel-labs/skills
     *
     * Many agents share `.agents/skills/` as their standard directory.
     * Using `amp` as the tool name covers all of them.
     */
    internal val AGENT_SKILLS_DIRS: Map<String, String> = mapOf(
      // .agents/skills/ — universal standard
      "amp" to ".agents/skills",
      "cursor" to ".agents/skills",
      "codex" to ".agents/skills",
      "github-copilot" to ".agents/skills",
      "gemini-cli" to ".agents/skills",
      "opencode" to ".agents/skills",
      // Agent-specific directories
      "claude-code" to ".claude/skills",
      "goose" to ".goose/skills",
      "windsurf" to ".windsurf/skills",
      "roo" to ".roo/skills",
    )

    /**
     * Resolves a tool name to its skills directory path.
     * Known agents use standard mappings; unknown names use `.{name}/skills` as fallback.
     */
    internal fun resolveSkillsDir(toolName: String): String {
      return AGENT_SKILLS_DIRS[toolName.trim().lowercase()]
        ?: ".${toolName.trim()}/skills"
    }

    /**
     * Merges extracted AGENTS.md content into an existing file's text.
     * - If [existing] is empty, creates a new file with wrapped injection markers.
     * - If [existing] contains injection markers, replaces the block between them.
     * - Otherwise, appends the wrapped injection block to the end.
     */
    internal fun mergeAgentsMd(
      existing: String,
      agentsContent: List<String>,
    ): String {
      val merged = agentsContent.joinToString("\n\n")
      val wrappedInjection =
        "$AGENTS_INJECTION_START\n$merged\n$AGENTS_INJECTION_END"
      return when {
        existing.contains(AGENTS_INJECTION_START) &&
          existing.contains(AGENTS_INJECTION_END) -> {
          val startPattern = Regex.escape(AGENTS_INJECTION_START)
          val endPattern = Regex.escape(AGENTS_INJECTION_END)
          existing.replace(
            Regex("$startPattern[\\s\\S]*?$endPattern"),
            wrappedInjection
          )
        }
        existing.isNotEmpty() -> {
          existing.trimEnd() + "\n\n" + wrappedInjection + "\n"
        }
        else -> wrappedInjection + "\n"
      }
    }

    /**
     * Scans a JAR file for AI context entries under [PREFIX].
     * Returns a pair of (agentsContent, skills) where skills are (relativePath, bytes).
     */
    internal fun scanJar(
      jarFile: java.io.File,
    ): Pair<List<String>, List<Pair<String, ByteArray>>> {
      val agentsContent = mutableListOf<String>()
      val skills = mutableListOf<Pair<String, ByteArray>>()
      JarFile(jarFile).use { jar ->
        jar.entries().asSequence()
          .filter { !it.isDirectory }
          .forEach { entry ->
            when {
              entry.name == AGENTS_FILE -> {
                val content = jar.getInputStream(entry).readBytes()
                agentsContent.add(String(content).trim())
              }
              entry.name.startsWith(SKILLS_PREFIX) -> {
                val relativePath = entry.name.removePrefix(SKILLS_PREFIX)
                if (relativePath.isNotEmpty()) {
                  val content = jar.getInputStream(entry).readBytes()
                  skills.add(relativePath to content)
                }
              }
            }
          }
      }
      return agentsContent to skills
    }
  }
}
