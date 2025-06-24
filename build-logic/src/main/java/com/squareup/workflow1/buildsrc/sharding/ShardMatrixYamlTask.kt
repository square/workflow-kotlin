package com.squareup.workflow1.buildsrc.sharding

import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.squareup.workflow1.buildsrc.diffString
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File
import javax.inject.Inject
import kotlin.LazyThreadSafetyMode.NONE

/**
 * This task manages test shard matrix configuration in a GitHub Actions workflow file,
 * ensuring that it matches the value of [numShards].
 *
 * @property yamlFile The CI configuration file this task works on.
 * @property startTagProperty The start tag to identify the matrix section in the CI configuration file.
 * @property endTagProperty The end tag to identify the matrix section in the CI configuration file.
 * @property numShards The number of shards to use for tests.
 * @property autoCorrect If `true`, the task will automatically correct any incorrect test shard
 *   matrix configurations.
 * @property updateTaskName The name of the task that updates the test shard matrix.
 */
abstract class ShardMatrixYamlTask
  @Inject
  constructor(
    objectFactory: ObjectFactory
  ) : DefaultTask() {
    /** kotlin.yml */
    @get:InputFile
    @get:PathSensitive(RELATIVE)
    val yamlFile = objectFactory.fileProperty()

    /**
     * Used to identify the start of the matrix.
     * Everything after this tag and before the end tag will be overwritten.
     *
     * ex: `### <start-my-shard-matrix>`
     */
    @get:Input abstract val startTagProperty: Property<String>
    private val startTag: String
      get() = startTagProperty.get()

    /**
     * Used to identify the end of the matrix.
     * ex: `### <end-my-shard-matrix>`
     * */
    @get:Input abstract val endTagProperty: Property<String>
    private val endTag: String
      get() = endTagProperty.get()

    /** for `3`, the matrix value would be `[ 1, 2, 3]` */
    @get:Input
    abstract val numShards: Property<Int>

    /**
     * If true the file will be updated. If false, the task will fail if the matrix is out of date.
     */
    @get:Input
    abstract val autoCorrect: Property<Boolean>

    @get:Input
    abstract val updateTaskName: Property<String>

    private val matrixSectionRegex by lazy(NONE) {

      val startTagEscaped = Regex.escape(startTag)
      val endTagEscaped = Regex.escape(endTag)

      Regex("""( *)(.*$startTagEscaped.*\n)[\s\S]+?(.*$endTagEscaped)""")
    }

    @TaskAction
    fun execute() {
      val ciFile = requireCiFile()

      val ciText = ciFile.readText()

      val newText = replaceYamlSections(ciText)

      if (ciText != newText) {

        if (autoCorrect.get()) {

          ciFile.writeText(newText)

          val message =
            "Updated the test shard matrix in the CI file.\n" +
              "\tfile://${yamlFile.get()}"

          services
            .get(StyledTextOutputFactory::class.java)
            .create("workflow-yaml-matrix")
            .withStyle(StyledTextOutput.Style.Description)
            .println(message)

          println()
          println(diffString(ciText, newText))
          println()
        } else {
          val message =
            "The test shard matrix in the CI file is out of date.\n" +
              "\tfile://${yamlFile.get()}\n\n" +
              "Run ./gradlew ${updateTaskName.get()} to automatically update."

          throw GradleException(message)
        }
      }
    }

    private fun replaceYamlSections(ciText: String): String {

      if (!ciText.contains(matrixSectionRegex)) {
        val message =
          "Couldn't find any `$startTag`/`$endTag` sections in the CI file:" +
            "\tfile://${yamlFile.get()}\n\n" +
            "\tSurround the matrix section with the comments '$startTag' and `$endTag':\n\n" +
            "\t    strategy:\n" +
            "\t      ### $startTag\n" +
            "\t      matrix:\n" +
            "\t        [ ... ]\n" +
            "\t      ### $endTag\n"

        throw GradleException(message)
      }

      return ciText.replace(matrixSectionRegex) { match ->

        val (indent, startTag, closingLine) = match.destructured

        val newContent = createYaml(indent, numShards.get())

        "$indent$startTag$newContent$closingLine"
      }
    }

    private fun requireCiFile(): File {
      val ciFile = yamlFile.get().asFile

      require(ciFile.exists()) {
        "Could not resolve file: file://$ciFile"
      }

      return ciFile
    }

    private fun createYaml(
      indent: String,
      numShards: Int
    ): String {
      val shardList = (1..numShards).joinToString(prefix = "[ ", postfix = " ]")
      return "${indent}shardNum: $shardList\n"
    }

    companion object {
      /**
       * Registers tasks to check and update the test shard matrix configuration in `kotlin.yml`.
       *
       * @param shardCount The number of test shards.
       * @param startTagName The start tag to identify the matrix section in `kotlin.yml`.
       * @param endTagName The end tag to identify the matrix section in `kotlin.yml`.
       * @param taskNamePart The part of the sharded task name which will be prepended to
       *   the matrix update task names.
       * @param yamlFile presumably `kotlin.yml`.
       */
      fun Project.registerYamlShardsTasks(
        shardCount: Int,
        startTagName: String,
        endTagName: String,
        taskNamePart: String,
        yamlFile: File
      ) {

        require(yamlFile.exists()) {
          "Could not resolve '$yamlFile'."
        }

        val updateName = "${taskNamePart}ShardMatrixYamlUpdate"
        val updateTask =
          tasks.register(
            updateName,
            ShardMatrixYamlTask::class.java
          ) { task ->
            task.yamlFile.set(yamlFile)
            task.numShards.set(shardCount)
            task.startTagProperty.set(startTagName)
            task.endTagProperty.set(endTagName)
            task.autoCorrect.set(true)
            task.updateTaskName.set(updateName)
          }

        val checkTask =
          tasks.register(
            "${taskNamePart}ShardMatrixYamlCheck",
            ShardMatrixYamlTask::class.java
          ) { task ->
            task.yamlFile.set(yamlFile)
            task.numShards.set(shardCount)
            task.startTagProperty.set(startTagName)
            task.endTagProperty.set(endTagName)
            task.autoCorrect.set(false)
            task.updateTaskName.set(updateName)
            task.mustRunAfter(updateTask)
          }

        // Automatically run this check task when running the `check` lifecycle task
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(checkTask)
      }
    }
  }
