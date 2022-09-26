package com.squareup.workflow1.buildsrc.dokka

import com.squareup.workflow1.VERSION_NAME
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.LazyThreadSafetyMode.NONE

abstract class DokkaVersionArchivePlugin : Plugin<Project> {

  lateinit var target: Project
  private val versionWithoutSnapshot by lazy(NONE) {
    target.VERSION_NAME.removeSuffix("-SNAPSHOT")
  }
  private val dokkaMultiModuleBuildDir by lazy(NONE) {
    target.rootDir.resolve("build/dokka/HtmlMultiModule")
  }
  private val currentVersionBuildDirZip by lazy(NONE) {
    dokkaMultiModuleBuildDir.resolveSibling("$versionWithoutSnapshot.zip")
  }
  private val dokkaArchiveBuildDir by lazy(NONE) {
    target.rootDir.resolve("build/tmp/dokka-archive")
  }
  private val dokkaArchive by lazy(NONE) {
    target.rootDir.resolve("dokka-archive")
  }

  override fun apply(target: Project) {
    if (target.rootProject != target) {
      throw GradleException("Only apply the dokka version archive plugin to a root project.")
    }

    this.target = target

    val unzip = registerUnzipTask()
    val zipDokkaArchive = registerZipTask()
    val syncTask = registerSyncTask(zipDokkaArchive)

    target.tasks.withType(DokkaMultiModuleTask::class.java).configureEach {
      dependsOn(unzip)
      finalizedBy(syncTask)
    }
  }

  private fun registerUnzipTask(): TaskProvider<Sync> = target.tasks
    .register("unzipDokkaArchives", Sync::class.java) {
      group = TASK_GROUP
      description = "Unzips all zip files in $dokkaArchive into $dokkaArchiveBuildDir"

      onlyIf { dokkaArchive.exists() }

      into(dokkaArchiveBuildDir)

      dokkaArchive
        .walkTopDown()
        .maxDepth(1)
        .filter { file -> file.isFile }
        .filter { file -> file.extension == "zip" }
        .filter { file -> file.nameWithoutExtension != versionWithoutSnapshot }
        .forEach { zipFile -> from(target.zipTree(zipFile)) }
    }

  private fun registerZipTask(): TaskProvider<Zip> = target.tasks
    .register("zipDokkaArchive", Zip::class.java) {
      group = TASK_GROUP
      description = "Zips the contents of $dokkaArchiveBuildDir"

      destinationDirectory.set(dokkaMultiModuleBuildDir.parentFile)
      archiveFileName.set(currentVersionBuildDirZip.name)
      outputs.file(currentVersionBuildDirZip)

      enabled = versionWithoutSnapshot == target.VERSION_NAME

      from(dokkaMultiModuleBuildDir) {
        into(versionWithoutSnapshot)
        // Don't copy the `older/` directory into the archive, because all navigation is done using
        // the root version's copy.  Archived `older/` directories just waste space.
        exclude("older/**")
      }

      mustRunAfter(target.tasks.withType(DokkaMultiModuleTask::class.java))
      dependsOn("dokkaHtmlMultiModule")
    }

  private fun registerSyncTask(zipDokkaArchive: TaskProvider<Zip>?): TaskProvider<Copy> =
    target.tasks.register("syncCurrentDokkaToDokkaArchive", Copy::class.java) {
      group = TASK_GROUP
      description = "sync the Dokka output from $dokkaArchiveBuildDir to " +
        "/dokka-archive/$versionWithoutSnapshot.zip"

      from(currentVersionBuildDirZip)
      into(dokkaArchive)
      outputs.file(dokkaArchive.resolve("$versionWithoutSnapshot.zip"))

      enabled = versionWithoutSnapshot == target.VERSION_NAME

      mustRunAfter(target.tasks.withType(DokkaMultiModuleTask::class.java))
      dependsOn(zipDokkaArchive)

      onlyIf {
        val destZip = dokkaArchive.resolve("$versionWithoutSnapshot.zip")

        !destZip.exists() || !currentVersionBuildDirZip.zipContentEquals(destZip)
      }
    }

  /** Compares the contents of two zip files, ignoring metadata like timestamps. */
  private fun File.zipContentEquals(other: File): Boolean {
    require(extension == "zip") { "This file is not a zip file: file://$path" }
    require(other.extension == "zip") { "This file is not a zip file: file://$other" }

    fun ZipFile.getZipEntries(): Set<ZipEntry> {
      return entries()
        .asSequence()
        .filter { !it.isDirectory }
        .toHashSet()
    }

    return ZipFile(this).use use1@{ zip1 ->
      ZipFile(other).use use2@{ zip2 ->

        val zip1Entries = zip1.getZipEntries()
        val zip1Names = zip1Entries.mapTo(mutableSetOf()) { it.name }
        val zip2Entries = zip2.getZipEntries()
        val zip2Names = zip2Entries.mapTo(mutableSetOf()) { it.name }

        // Check if any file is contained in one archive but not the other
        if (zip1Names != zip2Names) {
          return@use1 false
        }

        // Check if the contents of any files with the same path are different
        for (file in zip1Names) {
          val zip1Entry = zip1.getEntry(file)
          val zip2Entry = zip2.getEntry(file)

          if (zip1Entry.size != zip2Entry.size) {
            return@use1 false
          }

          val content1 = zip1.getInputStream(zip1Entry).use { it.readBytes() }
          val content2 = zip2.getInputStream(zip2Entry).use { it.readBytes() }

          if (!content1.contentEquals(content2)) {
            return@use1 false
          }
        }
        return@use1 true
      }
    }
  }

  companion object {

    private const val TASK_GROUP = "dokka versioning"
  }
}
