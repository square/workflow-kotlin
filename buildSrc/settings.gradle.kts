
enableFeaturePreview("VERSION_CATALOGS")

dependencyResolutionManagement {

  @Suppress("UnstableApiUsage")
  versionCatalogs {

    create("libs") {
      // Re-use the version catalog file from the main project
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
