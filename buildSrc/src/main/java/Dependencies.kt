@file:JvmName("Deps")

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

@Suppress("UnstableApiUsage")
val Project.catalogs: VersionCatalogsExtension
  get() = extensions.getByType(VersionCatalogsExtension::class)

@Suppress("UnstableApiUsage")
val Project.libsCatalog: VersionCatalog
  get() = catalogs.named("libs")

@Suppress("UnstableApiUsage")
fun VersionCatalog.dependency(alias: String): Provider<MinimalExternalModuleDependency> {
  return findDependency(alias).get()
}

object Versions {
  const val compileSdk = 31
  const val targetSdk = 30
}
