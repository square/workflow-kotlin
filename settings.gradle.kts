import de.fayard.dependencies.bootstrapRefreshVersionsAndDependencies

rootProject.name = "workflow"

include(
    ":internal-testing-utils",
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
    ":samples:todo-android:common",
    ":trace-encoder",
    ":workflow-core",
    ":workflow-runtime",
    ":workflow-rx2",
    ":workflow-testing",
    ":workflow-tracing",
    ":workflow-ui:backstack-common",
    ":workflow-ui:backstack-android",
    ":workflow-ui:core-common",
    ":workflow-ui:core-android",
    ":workflow-ui:modal-common",
    ":workflow-ui:modal-android"
)

// Include the tutorial build so the IDE sees it when syncing the main project.
includeBuild("samples/tutorial")

buildscript {
  repositories { gradlePluginPortal() }
  // See https://github.com/jmfayard/refreshVersions
  dependencies.classpath("de.fayard:dependencies:0.5.8")
}
bootstrapRefreshVersionsAndDependencies()
