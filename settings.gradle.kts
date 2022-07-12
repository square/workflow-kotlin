rootProject.name = "workflow"

enableFeaturePreview("VERSION_CATALOGS")

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
    // For binary compatibility validator.
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
    // For molecule SNAPSHOT
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/")}
  }
}

plugins {
  id("com.gradle.enterprise") version "3.8.1"
}

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    mavenCentral()
    google()
    // For molecule SNAPSHOT
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/")}
    // See androidx.dev (can use this for Snapshot builds of AndroidX)
    // maven { url = java.net.URI.create("https://androidx.dev/snapshots/builds/8224905/artifacts/repository") }
  }
}

include(
  ":benchmarks:dungeon-benchmark",
  ":benchmarks:performance-poetry:complex-benchmark",
  ":benchmarks:performance-poetry:complex-poetry",
  ":internal-testing-utils",
  ":samples:compose-samples",
  ":samples:containers:app-poetry",
  ":samples:containers:app-raven",
  ":samples:containers:android",
  ":samples:containers:common",
  ":samples:containers:hello-back-button",
  ":samples:containers:poetry",
  ":samples:dungeon:app",
  ":samples:dungeon:common",
  ":samples:dungeon:timemachine",
  ":samples:dungeon:timemachine-shakeable",
  ":samples:hello-terminal:hello-terminal-app",
  ":samples:hello-terminal:terminal-workflow",
  ":samples:hello-terminal:todo-terminal-app",
  ":samples:hello-workflow",
  ":samples:hello-workflow-fragment",
  ":samples:stub-visibility",
  ":samples:tictactoe:app",
  ":samples:tictactoe:common",
  ":samples:todo-android:app",
  ":trace-encoder",
  ":workflow-config:config-android",
  ":workflow-config:config-jvm",
  ":workflow-core",
  ":workflow-core-compose",
  ":workflow-runtime",
  ":workflow-rx2",
  ":workflow-testing",
  ":workflow-tracing",
  ":workflow-ui:compose",
  ":workflow-ui:compose-tooling",
  ":workflow-ui:core-common",
  ":workflow-ui:core-android",
  ":workflow-ui:internal-testing-android",
  ":workflow-ui:internal-testing-compose",
  ":workflow-ui:container-common",
  ":workflow-ui:container-android",
  ":workflow-ui:radiography"
)

// Include the tutorial build so the IDE sees it when syncing the main project.
includeBuild("samples/tutorial")
