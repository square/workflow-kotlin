package com.squareup.workflow1.buildsrc.artifacts

import com.squareup.workflow1.buildsrc.artifacts.ArtifactsCheckTask.Color.RESET
import com.squareup.workflow1.buildsrc.artifacts.ArtifactsCheckTask.Color.YELLOW
import org.gradle.api.GradleException
import org.gradle.api.file.ProjectLayout
import org.gradle.api.tasks.TaskAction
import java.util.Locale
import javax.inject.Inject

/**
 * Evaluates all published artifacts in the project and compares the results to `/artifacts.json`.
 *
 * If there are any differences, the task will fail with a descriptive message.
 */
open class ArtifactsCheckTask @Inject constructor(
  projectLayout: ProjectLayout
) : ArtifactsTask(projectLayout) {

  init {
    description = "Parses the Maven artifact parameters for all modules " +
      "and compares them to those recorded in artifacts.json"
    group = "verification"
  }

  @TaskAction
  fun run() {

    val fromJson = moshiAdapter.fromJson(reportFile.asFile.readText())
      .orEmpty()
      .associateBy { it.gradlePath }

    val currentPaths = currentList.mapTo(mutableSetOf()) { it.gradlePath }

    val extraFromJson = fromJson.values.filterNot { it.gradlePath in currentPaths }
    val extraFromCurrent = currentList.filterNot { it.gradlePath in fromJson.keys }

    val changed = currentList.minus(fromJson.values)
      .minus(extraFromCurrent)
      .map { artifact ->
        fromJson.getValue(artifact.gradlePath) to artifact
      }

    // Each artifact needs to have a unique ID.  Repository managers will quietly allow overwrites
    // with duplicate IDs, so this is the last chance to catch it before publishing.
    val duplicateArtifactIds = currentList.findDuplicates { artifactId }

    // This is mostly superficial, but it ensures that a copy/pasted module also doesn't retain the
    // original's pom description.
    val duplicateDescriptions = currentList.findDuplicates { description }

    val foundSomething = sequenceOf(
      duplicateArtifactIds.keys,
      duplicateDescriptions.keys,
      extraFromJson,
      extraFromCurrent,
      changed
    ).any { it.isNotEmpty() }

    if (foundSomething) {
      reportChanges(
        duplicateArtifactIds = duplicateArtifactIds,
        duplicatePomNames = duplicateDescriptions,
        missing = extraFromJson,
        extraFromCurrent = extraFromCurrent,
        changed = changed
      )
    }
  }

  private fun <R : Comparable<R>> List<ArtifactConfig>.findDuplicates(
    selector: ArtifactConfig.() -> R
  ): Map<R, List<ArtifactConfig>> = groupBy(selector)
    .filter { it.value.size > 1 }

  private fun reportChanges(
    duplicateArtifactIds: Map<String, List<ArtifactConfig>>,
    duplicatePomNames: Map<String, List<ArtifactConfig>>,
    missing: List<ArtifactConfig>,
    extraFromCurrent: List<ArtifactConfig>,
    changed: List<Pair<ArtifactConfig, ArtifactConfig>>
  ) {

    val message = buildString {

      appendLine(
        "\tArtifact definitions don't match.  If this is intended, " +
          "run `./gradlew artifactsDump` and commit changes."
      )
      appendLine()

      maybeAddDuplicateValueMessages(duplicateArtifactIds, "artifact id")
      maybeAddDuplicateValueMessages(duplicatePomNames, "pom name")

      maybeAddMissingArtifactMessages(missing)

      maybeAddExtraArtifactMessages(extraFromCurrent)

      maybeAddChangedValueMessages(changed)
    }

    logger.error(message.colorized(YELLOW))

    throw GradleException("Artifacts check failed")
  }

  private fun StringBuilder.maybeAddDuplicateValueMessages(
    duplicates: Map<String, List<ArtifactConfig>>,
    propertyName: String
  ) = apply {

    if (duplicates.isNotEmpty()) {
      appendLine("\tDuplicate properties were found where they should be unique:")
      appendLine()
      duplicates.forEach { (value, artifacts) ->
        appendLine("\t\t       projects - ${artifacts.map { it.gradlePath }}")
        appendLine("\t\t       property - $propertyName")
        appendLine("\t\tduplicate value - $value")
        appendLine()
      }
    }
  }

  private fun StringBuilder.maybeAddMissingArtifactMessages(
    missing: List<ArtifactConfig>
  ) = apply {

    if (missing.isNotEmpty()) {
      val isAre = if (missing.size == 1) "is" else "are"
      appendLine(
        "\t${pluralsString(missing.size)} defined in `artifacts.json` but " +
          "$isAre duplicates from the project:"
      )
      appendLine()
      missing.forEach {
        appendLine(it.message())
        appendLine()
      }
    }
  }

  private fun StringBuilder.maybeAddExtraArtifactMessages(
    extraFromCurrent: List<ArtifactConfig>
  ) = apply {

    if (extraFromCurrent.isNotEmpty()) {
      appendLine("\t${pluralsString(extraFromCurrent.size)} new:\n")
      extraFromCurrent.forEach {
        appendLine(it.message())
        appendLine()
      }
    }
  }

  private fun StringBuilder.maybeAddChangedValueMessages(
    changed: List<Pair<ArtifactConfig, ArtifactConfig>>
  ): StringBuilder = apply {

    fun appendDiff(
      propertyName: String,
      old: String,
      new: String
    ) {
      appendLine("\t\t\told $propertyName - $old")
      appendLine("\t\t\tnew $propertyName - $new")
    }

    if (changed.isNotEmpty()) {
      appendLine("\t${pluralsString(changed.size)} changed:")
      changed.forEach { (old, new) ->

        appendLine()
        appendLine("\t    ${old.gradlePath} -")

        if (old.group != new.group) {
          appendDiff("group", old.group, new.group)
        }

        if (old.artifactId != new.artifactId) {
          appendDiff("artifact id", old.artifactId, new.artifactId)
        }

        if (old.description != new.description) {
          appendDiff("pom name", old.description, new.description)
        }

        if (old.packaging != new.packaging) {
          appendDiff("packaging", old.packaging, new.packaging)
        }
      }
      appendLine()
    }
  }

  private fun pluralsString(size: Int): String {
    return if (size == 1) "This artifact is"
    else "These artifacts are"
  }

  private fun ArtifactConfig.message(): String {
    return """
            |                gradlePath  - $gradlePath
            |                group       - $group
            |                artifactId  - $artifactId
            |                pom name    - $description
            |                packaging   - $packaging
    """.trimMargin()
  }

  enum class Color(val escape: String) {
    RESET("\u001B[0m"),
    YELLOW("\u001B[33m")
  }

  private val supported = "win" !in System.getProperty("os.name").toLowerCase(Locale.ROOT)
  private fun String.colorized(color: Color) = if (supported) {
    "${color.escape}$this${RESET.escape}"
  } else {
    this
  }
}
