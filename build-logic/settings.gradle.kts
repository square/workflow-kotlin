plugins {
  // Hardcoded as this is upstream of the version catalog. Keep this in sync with that.
  kotlin("jvm") version "2.3.10" apply false
}

dependencyResolutionManagement {

  versionCatalogs {

    create("libs") {
      // Re-use the version catalog file from the main project
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
