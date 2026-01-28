package com.squareup.workflow1.buildsrc

import com.squareup.workflow1.buildsrc.sharding.ShardMatrixYamlTask.Companion.registerYamlShardsTasks
import org.gradle.api.GradleException
import org.gradle.api.Project
import kotlin.LazyThreadSafetyMode.NONE

private const val SHARD_COUNT = 3

/**
 * Create "shard" tasks which collectively depend upon all Android `connectedCheck` tasks in the
 * entire project.
 *
 * Each shard depends upon the `connectedCheck` tasks of some subset of Android projects.
 * Projects are assigned to a shard by counting the number of `@Test` annotations within their
 * `androidTest` directory, then associating those projects to a shard in a round-robin fashion.
 *
 * These shards are invoked in CI using a GitHub Actions matrix. If the number of shards changes,
 * the `connectedCheckShardMatrixYamlUpdate` task can automatically update the workflow file so
 * that they're all invoked.
 *
 * The shard tasks are invoked as:
 * ```shell
 * # roughly 1/3 of the tests
 * ./gradlew connectedCheckShard1
 * # the second third
 * ./gradlew connectedCheckShard2
 * # the last third
 * ./gradlew connectedCheckShard3
 * ```
 *
 * @param target the root project which gets the shard tasks
 */
fun shardConnectedCheckTasks(target: Project) {
  if (target != target.rootProject) {
    throw GradleException("Only add connectedCheck shard tasks from the root project.")
  }

  target.registerYamlShardsTasks(
    shardCount = SHARD_COUNT,
    startTagName = "### <start-connected-check-shards>",
    endTagName = "### <end-connected-check-shards>",
    taskNamePart = "connectedCheck",
    yamlFile = target.rootProject.file(".github/workflows/kotlin.yml")
  )

  // Calculate the cost of each project's tests
  val projectsWithTestCount = lazy(NONE) {
    target.subprojects
      .filter {
        // Only Android projects can have these tasks.
        // Use the KGP Android plugin instead of AGP since KGP has only one ID to look for.
        it.plugins.hasPlugin("org.jetbrains.kotlin.android") ||
          it.plugins.hasPlugin("com.android.kotlin.multiplatform.library")
      }
      .map { it to it.androidTestCost() }
  }

  // Assign each project to a shard.
  // The values are lazy so that the work only happens at task configuration time, but they're
  // outside the task configuration block so that it only happens once.
  val shardAssignments = projectsWithTestCount.shards()

  // We use connectedAndroidTest instead of connectedCheck since Android instrumented tests in KMP
  // modules aren't included the latter, but they are in the former.
  val connectedTestName = "connectedAndroidTest"

  shardAssignments.forEach { shard ->

    val projects by shard.projectsLazy

    val paths by lazy {
      projects.joinToString(prefix = "[ ", postfix = " ]") { it.path }
    }

    target.tasks.register("connectedCheckShard${shard.number}") { task ->

      task.group = "Verification"

      validateSharding(
        projectsWithTestCount = projectsWithTestCount.value,
        shardAssignments = shardAssignments
      )

      task.description = "Runs $connectedTestName in projects: $paths"

      val assignedTests = projects.map { project ->
        project.tasks.matching { it.name == connectedTestName }
      }

      task.dependsOn(assignedTests)
    }

    target.tasks.register("prepareConnectedCheckShard${shard.number}") { task ->

      validateSharding(
        projectsWithTestCount = projectsWithTestCount.value,
        shardAssignments = shardAssignments
      )

      task.description = "Builds all artifacts for running connected tests in projects: $paths"

      val regex = Regex("""prepare[A-Z]\w*AndroidTestArtifacts""")

      val prepareTasks = projects.map { project ->
        project.tasks.matching { it.name.matches(regex) }
      }

      task.dependsOn(prepareTasks)
    }
  }
}

/**
 * Assigns each project to a shard, distributing them by the number of tests they have.
 * The combined test costs of all shards should be approximately equal.
 *
 * There's a lot of `Lazy<T>` here so that defer parsing all the tests until task configuration.
 * If the tasks aren't actually being invoked, no parsing happens.
 *
 * @receiver Every project with its associated test cost.
 * @return A list of shards, where each shard encapsulates a subset of projects.
 */
private fun Lazy<List<Pair<Project, Int>>>.shards(): List<Shard> {

  val shards by lazy {
    List<MutableList<Pair<Project, Int>>>(SHARD_COUNT) { mutableListOf() }
      .also { shards ->

        fun next(): MutableList<Pair<Project, Int>> {
          return shards.minBy { it.sumOf { (_, count) -> count } }
        }

        // Sort the projects by descending test cost, then fall back to the project paths
        // The path sort is just so that the shard composition is stable.  If the shard composition
        // isn't stable, the shard tasks may not be up-to-date and build caching in CI is broken.
        val sorted = value.sortedWith(compareBy({ it.second }, { it.first }))
          .reversed()

        for (pair in sorted) {
          next().add(pair)
        }
      }
  }

  return List(SHARD_COUNT) { index ->
    Shard(
      number = index + 1,
      testCountLazy = lazy { shards[index].sumOf { (_, count) -> count } },
      projectsLazy = lazy { shards[index].map { (project, _) -> project } }
    )
  }
}

private data class Shard(
  val number: Int,
  val testCountLazy: Lazy<Int>,
  val projectsLazy: Lazy<List<Project>>
) {
  val testCount by testCountLazy
  val projects by projectsLazy
  override fun toString(): String {
    return "Shard(number=$number, testCount=$testCount, projects=${projects.joinToString("\n") { it.path }})"
  }
}

private fun validateSharding(
  projectsWithTestCount: List<Pair<Project, Int>>,
  shardAssignments: List<Shard>,
) {

  val allShardsText by lazy(NONE) { shardAssignments.joinToString("\n") }

  if (shardAssignments.size != SHARD_COUNT) {
    throw GradleException(
      "Unexpected shard configuration.  There should be $SHARD_COUNT shards, " +
        "but `shardAssignments` is:\n$allShardsText"
    )
  }

  val allShardedProjects = shardAssignments.flatMap { it.projects }

  val duplicates = allShardedProjects.groupingBy { it }
    .eachCount()
    .filter { it.value > 1 }
    .keys

  if (duplicates.isNotEmpty()) {
    throw GradleException(
      "There are duplicated projects in shards.\n" +
        "Duplicated projects: ${duplicates.map { it.path }}\n" +
        "All shards:\n$allShardsText"
    )
  }

  val missingInShards = projectsWithTestCount
    .map { it.first }
    .minus(allShardedProjects.toSet())

  if (missingInShards.isNotEmpty()) {
    throw GradleException(
      "There are projects missing from all shards.\n" +
        "Missing projects: $missingInShards\n" +
        "All shards:\n$allShardsText"
    )
  }
}

/**
 * matches:
 * ```
 * @org.junit.Test
 * @Test
 * ```
 */
private val testAnnotationRegex = """@(?:org\.junit\.)?Test\s+""".toRegex()

/**
 * Counts all the `androidTest` functions annotated with `@Test` within this project.
 *
 * Each test function has a cost of 1. A project with 20 tests has a cost of 20.
 */
private fun Project.androidTestCost(): Int =
  listOf(
    // Android-only, non-KMP modules.
    file("src/androidTest/java"),
    // KMP modules with android targets.
    file("src/androidDeviceTest/kotlin"),
  ).sumOf { androidTestSrc ->
    if (!androidTestSrc.exists()) return@sumOf 0

    androidTestSrc
    .walkTopDown()
    .filter { it.isFile && it.extension == "kt" }
    .sumOf { file ->
      val fileText = file.readText()

      testAnnotationRegex.findAll(fileText).count()
    }
}
