package com.squareup.workflow1

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

val Project.VERSION_NAME: String
  get() = property("VERSION_NAME") as String

val Project.catalogs: VersionCatalogsExtension
  get() = extensions.getByType(VersionCatalogsExtension::class)

val Project.libsCatalog: VersionCatalog
  get() = catalogs.named("libs")

fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> {
  return findLibrary(alias).get()
}

fun VersionCatalog.version(alias: String): String {
  return findVersion(alias).get().requiredVersion
}
