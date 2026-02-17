package com.squareup.workflow1.buildsrc.airules

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that discovers runtime classpath archives and delegates extraction
 * to `extract-ai-rules.main.kts` — the single implementation of the
 * AI rules/skills extraction logic.
 *
 * The script is bundled as a classpath resource in this plugin. At execution time,
 * the task writes the discovered JAR paths to a temp file and invokes the script
 * via `kotlin`.
 */
public open class AiRulesExtractTask : DefaultTask() {

  @TaskAction
  public fun extract() {
    val archiveFiles = collectClasspathArchives()
    logger.lifecycle(
      "Discovered ${archiveFiles.size} classpath archives to scan for AI rules/skills..."
    )

    if (archiveFiles.isEmpty()) {
      logger.lifecycle("No classpath archives found. Nothing to extract.")
      return
    }

    // Write JAR paths to a temp file for the script to read
    val jarsListFile = File.createTempFile("ai-rules-jars-", ".txt").apply {
      deleteOnExit()
      writeText(archiveFiles.joinToString("\n") { it.absolutePath })
    }

    // Extract the script from classpath resources to a temp file
    val scriptFile = File.createTempFile("extract-ai-rules-", ".main.kts").apply {
      deleteOnExit()
    }
    val scriptContent = javaClass.classLoader.getResourceAsStream("extract-ai-rules.main.kts")
      ?: throw GradleException(
        "Could not find extract-ai-rules.main.kts on the plugin classpath. " +
          "Ensure the script is bundled as a resource in build-logic-ai-rules."
      )
    scriptFile.writeBytes(scriptContent.readBytes())

    // Invoke the script via `kotlin`
    val result = project.exec { exec ->
      exec.commandLine(
        "kotlin",
        scriptFile.absolutePath,
        "--jars-file", jarsListFile.absolutePath,
        "--output-dir", project.projectDir.absolutePath,
      )
      exec.isIgnoreExitValue = true
    }

    if (result.exitValue != 0) {
      throw GradleException(
        "extract-ai-rules.main.kts failed with exit code ${result.exitValue}. " +
          "Ensure 'kotlin' is available on your PATH (install via SDKMAN, Homebrew, etc.)."
      )
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
}
