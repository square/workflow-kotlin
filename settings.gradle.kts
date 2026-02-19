rootProject.name = "workflow"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
    // For binary compatibility validator.
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
  }
  includeBuild("build-logic")
}

plugins {
  id("com.gradle.enterprise") version "3.16.2"
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
    // See androidx.dev (can use this for Snapshot builds of AndroidX)
    // maven { url = java.net.URI.create("https://androidx.dev/snapshots/builds/8224905/artifacts/repository") }
  }
}

include(
  ":benchmarks:dungeon-benchmark",
  ":benchmarks:performance-poetry:complex-benchmark",
  ":benchmarks:performance-poetry:complex-poetry",
  ":benchmarks:runtime-microbenchmark",
  ":samples:compose-samples",
  ":samples:containers:android",
  ":samples:containers:app-poetry",
  ":samples:containers:app-raven",
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
  ":samples:nested-overlays",
  ":samples:stub-visibility",
  ":samples:tictactoe:app",
  ":samples:tictactoe:common",
  ":samples:todo-android:app",
  ":samples:runtime-library:app",
  ":samples:runtime-library:lib",
  ":workflow-config:config-android",
  ":workflow-config:config-jvm",
  ":workflow-core",
  ":workflow-runtime",
  ":workflow-rx2",
  ":workflow-testing",
  ":workflow-trace-viewer",
  ":workflow-tracing",
  ":workflow-tracing-papa",
  ":workflow-ui:compose",
  ":workflow-ui:compose-tooling",
  ":workflow-ui:core-android",
  ":workflow-ui:core-common",
  ":workflow-ui:internal-testing-android",
  ":workflow-ui:internal-testing-compose",
  ":workflow-ui:radiography",
  ":workflow-ai-context"
)

// Include the tutorial build so the IDE sees it when syncing the main project.
includeBuild("samples/tutorial")
