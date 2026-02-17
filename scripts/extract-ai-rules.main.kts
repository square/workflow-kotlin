#!/usr/bin/env kotlin

/**
 * Standalone script to extract AI rules and skills from workflow-kotlin JARs/AARs.
 *
 * This is the SINGLE implementation of the extraction logic, used by both:
 * - Direct invocation: `kotlin extract-ai-rules.main.kts [options]`
 * - Gradle plugin: `AiRulesExtractTask` delegates to this script
 *
 * Usage:
 *   kotlin extract-ai-rules.main.kts [options]
 *
 * Options:
 *   --jars-dir <path>     Scan a directory tree for JARs/AARs (recursive)
 *   --jars-file <path>    Read JAR/AAR paths from a file (one per line)
 *   --output-dir <path>   Output directory (default: current directory)
 *   --tools <list>        Comma-separated tool names (default: firebender,cursor,claude)
 *
 * JAR discovery (in priority order):
 *   1. --jars-file: reads exact JAR/AAR paths from a file (used by Gradle task)
 *   2. --jars-dir: recursively scans a directory for .jar/.aar files
 *   3. Default: scans ~/.gradle/caches for com.squareup.workflow1 artifacts
 */

import java.io.File
import java.util.jar.JarFile

val PREFIX = "META-INF/com.squareup.workflow1/"
val RULES_PREFIX = "${PREFIX}rules/"
val SKILLS_PREFIX = "${PREFIX}skills/"
val AGENTS_FILE = "${PREFIX}AGENTS.md"
val AGENTS_INJECTION_SLUG = "workflow-kotlin-AGENTS-injection"
val AGENTS_INJECTION_START = "<!-- $AGENTS_INJECTION_SLUG:START -->"
val AGENTS_INJECTION_END = "<!-- $AGENTS_INJECTION_SLUG:END -->"

fun List<String>.argValue(name: String): String? {
  val idx = indexOf(name)
  return if (idx >= 0 && idx + 1 < size) this[idx + 1] else null
}

// Parse arguments
val argsList = args.toList()
val jarsFile = argsList.argValue("--jars-file")
val jarsDir = argsList.argValue("--jars-dir")
val outputDir = File(argsList.argValue("--output-dir") ?: ".")
val tools = (argsList.argValue("--tools") ?: "firebender,cursor,claude")
  .split(",")
  .map { ".${it.trim()}" }

// Find archives (priority: --jars-file > --jars-dir > Gradle cache scan)
val archiveFiles: List<File> = when {
  jarsFile != null -> {
    // Read JAR/AAR paths from a file (one per line), used by Gradle task integration
    val file = File(jarsFile)
    if (!file.exists()) {
      System.err.println("Error: jars-file not found: $jarsFile")
      emptyList()
    } else {
      file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { File(it) }
        .filter { it.exists() && (it.extension == "jar" || it.extension == "aar") }
    }
  }
  jarsDir != null -> {
    File(jarsDir).walkTopDown()
      .filter { it.extension == "jar" || it.extension == "aar" }
      .toList()
  }
  else -> {
    // Scan Gradle caches for com.squareup.workflow1 artifacts
    val gradleCache = File(System.getProperty("user.home"), ".gradle/caches")
    if (gradleCache.exists()) {
      gradleCache.walkTopDown()
        .filter {
          (it.extension == "jar" || it.extension == "aar") &&
            it.path.contains("com.squareup.workflow1")
        }
        .toList()
    } else {
      emptyList()
    }
  }
}

println("Found ${archiveFiles.size} archive files to scan")

var rulesCount = 0
var skillsCount = 0
var agentsCount = 0

for (jar in archiveFiles) {
  try {
    JarFile(jar).use { jarFile ->
      jarFile.entries().asSequence()
        .filter { !it.isDirectory }
        .forEach { entry ->
          val (prefix, subDir) = when {
            entry.name == AGENTS_FILE -> "" to ""
            entry.name.startsWith(RULES_PREFIX) -> RULES_PREFIX to "rules"
            entry.name.startsWith(SKILLS_PREFIX) -> SKILLS_PREFIX to "skills"
            else -> return@forEach
          }

          val relativePath = if (entry.name == AGENTS_FILE) {
            "AGENTS.md"
          } else {
            entry.name.removePrefix(prefix)
          }
          if (relativePath.isEmpty()) return@forEach

          val content = jarFile.getInputStream(entry).readBytes()

          if (entry.name == AGENTS_FILE) {
            val targetFile = File(outputDir, "AGENTS.md")
            targetFile.parentFile.mkdirs()
            val injectedContent = String(content).trim()
            val wrappedInjection = "$AGENTS_INJECTION_START\n$injectedContent\n$AGENTS_INJECTION_END"
            val existing = if (targetFile.exists()) targetFile.readText() else ""
            if (existing.contains(AGENTS_INJECTION_START) && existing.contains(AGENTS_INJECTION_END)) {
              val updated = existing.replace(
                Regex("$AGENTS_INJECTION_START[\\s\\S]*?$AGENTS_INJECTION_END"),
                wrappedInjection
              )
              targetFile.writeText(updated)
            } else if (existing.isNotEmpty()) {
              val updated = existing.trimEnd() + "\n\n" + wrappedInjection + "\n"
              targetFile.writeText(updated)
            } else {
              targetFile.writeText(wrappedInjection + "\n")
            }
          } else {
            for (toolDir in tools) {
              val targetPath = "$toolDir/$subDir/$relativePath"
              val targetFile = File(outputDir, targetPath)
              targetFile.parentFile.mkdirs()
              targetFile.writeBytes(content)
            }
          }

          when (subDir) {
            "rules" -> rulesCount++
            "skills" -> skillsCount++
            else -> agentsCount++
          }
          val label = if (subDir.isEmpty()) relativePath else "$subDir/$relativePath"
          println("  Extracted: $label (from ${jar.name})")
        }
    }
  } catch (_: Exception) {
    // Skip non-JAR files
  }
}

println()
println("Done! Extracted $rulesCount rules, $skillsCount skills, and $agentsCount AGENTS files")
println("Target directories: ${tools.map { File(outputDir, it).absolutePath }}")
