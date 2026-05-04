package com.squareup.workflow1.ai.context

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ai.context.AiContextExtractTask.Companion.AGENTS_FILE
import com.squareup.workflow1.ai.context.AiContextExtractTask.Companion.AGENTS_INJECTION_END
import com.squareup.workflow1.ai.context.AiContextExtractTask.Companion.AGENTS_INJECTION_START
import com.squareup.workflow1.ai.context.AiContextExtractTask.Companion.SKILLS_PREFIX
import com.squareup.workflow1.ai.context.AiContextExtractTask.Companion.mergeAgentsMd
import com.squareup.workflow1.ai.context.AiContextExtractTask.Companion.scanJar
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test

internal class AiContextExtractTaskTest {

  @get:Rule
  val tmpDir = TemporaryFolder()

  // region mergeAgentsMd

  @Test fun `mergeAgentsMd creates new file when existing is empty`() {
    val result = mergeAgentsMd("", listOf("# Hello"))
    assertThat(result).isEqualTo(
      "$AGENTS_INJECTION_START\n# Hello\n$AGENTS_INJECTION_END\n"
    )
  }

  @Test fun `mergeAgentsMd appends to existing file without injection markers`() {
    val existing = "# My Project\n\nSome existing content.\n"
    val result = mergeAgentsMd(existing, listOf("# Injected"))
    assertThat(result).isEqualTo(
      "# My Project\n\nSome existing content.\n\n" +
        "$AGENTS_INJECTION_START\n# Injected\n$AGENTS_INJECTION_END\n"
    )
  }

  @Test fun `mergeAgentsMd replaces existing injection block`() {
    val existing = "# My Project\n\n" +
      "$AGENTS_INJECTION_START\n# Old Content\n$AGENTS_INJECTION_END\n\n" +
      "# Footer\n"
    val result = mergeAgentsMd(existing, listOf("# New Content"))
    assertThat(result).isEqualTo(
      "# My Project\n\n" +
        "$AGENTS_INJECTION_START\n# New Content\n$AGENTS_INJECTION_END\n\n" +
        "# Footer\n"
    )
  }

  @Test fun `mergeAgentsMd joins multiple content blocks`() {
    val result = mergeAgentsMd("", listOf("# Block 1", "# Block 2"))
    assertThat(result).isEqualTo(
      "$AGENTS_INJECTION_START\n# Block 1\n\n# Block 2\n$AGENTS_INJECTION_END\n"
    )
  }

  @Test fun `mergeAgentsMd trims trailing whitespace before appending`() {
    val existing = "# Project\n\n\n\n"
    val result = mergeAgentsMd(existing, listOf("# Injected"))
    assertThat(result).isEqualTo(
      "# Project\n\n" +
        "$AGENTS_INJECTION_START\n# Injected\n$AGENTS_INJECTION_END\n"
    )
  }

  // endregion

  // region scanJar

  @Test fun `scanJar extracts AGENTS md from JAR`() {
    val jar = createTestJar(
      AGENTS_FILE to "# Test Agents Content"
    )
    val (agents, skills) = scanJar(jar)
    assertThat(agents).containsExactly("# Test Agents Content")
    assertThat(skills).isEmpty()
  }

  @Test fun `scanJar extracts skills from JAR`() {
    val jar = createTestJar(
      "${SKILLS_PREFIX}create-workflow/SKILL.md" to "# Create Workflow"
    )
    val (agents, skills) = scanJar(jar)
    assertThat(agents).isEmpty()
    assertThat(skills).hasSize(1)
    assertThat(skills[0].first).isEqualTo("create-workflow/SKILL.md")
    assertThat(String(skills[0].second)).isEqualTo("# Create Workflow")
  }

  @Test fun `scanJar extracts both agents and skills`() {
    val jar = createTestJar(
      AGENTS_FILE to "# Agents",
      "${SKILLS_PREFIX}workflow-testing/SKILL.md" to "# Testing",
      "${SKILLS_PREFIX}create-workflow/SKILL.md" to "# Create"
    )
    val (agents, skills) = scanJar(jar)
    assertThat(agents).containsExactly("# Agents")
    assertThat(skills).hasSize(2)
    assertThat(skills.map { it.first }).containsExactly(
      "workflow-testing/SKILL.md",
      "create-workflow/SKILL.md"
    )
  }

  @Test fun `scanJar ignores entries outside prefix`() {
    val jar = createTestJar(
      "com/example/SomeClass.class" to "bytecode",
      "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0"
    )
    val (agents, skills) = scanJar(jar)
    assertThat(agents).isEmpty()
    assertThat(skills).isEmpty()
  }

  @Test fun `scanJar trims whitespace from agents content`() {
    val jar = createTestJar(
      AGENTS_FILE to "\n  # Content With Whitespace  \n\n"
    )
    val (agents, _) = scanJar(jar)
    assertThat(agents).containsExactly("# Content With Whitespace")
  }

  // endregion

  // region plugin functional tests

  @Test fun `extractAiContext writes agents and skills from classpath archives`() {
    val projectDir = tmpDir.newFolder("consumer-project")
    writeConsumerBuild(projectDir)
    createTestJar(
      File(projectDir, "libs/workflow-context.jar"),
      AGENTS_FILE to "# Workflow Guidance",
      "${SKILLS_PREFIX}create-workflow/SKILL.md" to "# Create Workflow"
    )

    runGradle(projectDir, "extractAiContext")

    assertThat(File(projectDir, "AGENTS.md").readText()).isEqualTo(
      "$AGENTS_INJECTION_START\n# Workflow Guidance\n$AGENTS_INJECTION_END\n"
    )
    assertThat(File(projectDir, ".agents/skills/create-workflow/SKILL.md").readText())
      .isEqualTo("# Create Workflow")
  }

  @Test fun `extractAiContext supports configured agents file and skills directories`() {
    val projectDir = tmpDir.newFolder("configured-consumer-project")
    writeConsumerBuild(
      projectDir,
      """
      aiContext {
        outputDirectory.set(layout.projectDirectory.dir("ai-output"))
        agentsFile.set(layout.projectDirectory.file(".github/copilot-instructions.md"))
        skillsDirectories.set(['team/skills', '.custom-agent/skills'])
      }
      """.trimIndent()
    )
    createTestJar(
      File(projectDir, "libs/workflow-context.jar"),
      AGENTS_FILE to "# Workflow Guidance",
      "${SKILLS_PREFIX}workflow-testing/SKILL.md" to "# Workflow Testing"
    )

    runGradle(projectDir, "extractAiContext")

    assertThat(File(projectDir, ".github/copilot-instructions.md").readText()).isEqualTo(
      "$AGENTS_INJECTION_START\n# Workflow Guidance\n$AGENTS_INJECTION_END\n"
    )
    assertThat(File(projectDir, "ai-output/team/skills/workflow-testing/SKILL.md").readText())
      .isEqualTo("# Workflow Testing")
    assertThat(
      File(projectDir, "ai-output/.custom-agent/skills/workflow-testing/SKILL.md").readText()
    ).isEqualTo("# Workflow Testing")
  }

  // endregion

  private fun createTestJar(vararg entries: Pair<String, String>): File {
    val jarFile = File(tmpDir.root, "test-${entries.hashCode()}.jar")
    createTestJar(jarFile, *entries)
    return jarFile
  }

  private fun createTestJar(
    jarFile: File,
    vararg entries: Pair<String, String>,
  ): File {
    jarFile.parentFile.mkdirs()
    JarOutputStream(jarFile.outputStream()).use { jos ->
      for ((path, content) in entries) {
        jos.putNextEntry(JarEntry(path))
        jos.write(content.toByteArray())
        jos.closeEntry()
      }
    }
    return jarFile
  }

  private fun writeConsumerBuild(
    projectDir: File,
    aiContextConfiguration: String = "",
  ) {
    File(projectDir, "settings.gradle").writeText("")
    File(projectDir, "build.gradle").writeText(
      """
      plugins {
        id 'java'
        id 'com.squareup.workflow1.ai-context'
      }

      dependencies {
        implementation files('libs/workflow-context.jar')
      }

      $aiContextConfiguration
      """.trimIndent()
    )
  }

  private fun runGradle(
    projectDir: File,
    vararg arguments: String,
  ) {
    GradleRunner.create()
      .withProjectDir(projectDir)
      .withArguments(arguments.toList() + "--stacktrace")
      .withPluginClasspath()
      .build()
  }
}
