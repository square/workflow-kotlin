plugins {
  // Hardcoded as this is upstream of the version catalog. Keep this in sync with that.
  kotlin("jvm") version "1.9.24" apply false
}

dependencyResolutionManagement {

  @Suppress("UnstableApiUsage")
  versionCatalogs {

    create("libs") {
      // Re-use the version catalog file from the main project
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
