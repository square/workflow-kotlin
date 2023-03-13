package com.squareup.workflow1.buildsrc.internal

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer

val Project.catalogs: VersionCatalogsExtension
  get() = extensions.getByType(VersionCatalogsExtension::class.java)

val Project.libsCatalog: VersionCatalog
  get() = catalogs.named("libs")

fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> {
  return findLibrary(alias).get()
}

fun VersionCatalog.version(alias: String): String {
  return findVersion(alias).get().requiredVersion
}

fun DependencyHandler.implementation(notation: Any) {
  add("implementation", notation)
}

fun DependencyHandler.androidTestImplementation(notation: Any) {
  add("androidTestImplementation", notation)
}

operator fun DependencyHandler.invoke(
  config: DependencyHandler.() -> Unit
): DependencyHandler = apply { config(this) }

// See https://stackoverflow.com/questions/25324880/detect-ide-environment-with-gradle
val Project.isRunningFromIde
  get() = properties["android.injected.invoked.from.ide"] == "true"

fun DependencyHandler.kotlin(
  module: String,
  version: String? = null
): Any = "org.jetbrains.kotlin:kotlin-$module${version?.let { ":$version" } ?: ""}"
