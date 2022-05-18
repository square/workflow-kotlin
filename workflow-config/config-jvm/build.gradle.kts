plugins {
  `java-library`
  `kotlin-jvm`
  id("org.jetbrains.dokka")
}

tasks.withType<Test> {
  project
    .properties
    .asSequence()
    .filter { (key, value) ->
      key.startsWith("workflow.runtime") && value != null
    }
    .forEach { (key, value) ->
      println("Workflow Runtime Configuration via test: '$key': '$value'")
      System.setProperty(key, value as String)
      systemProperty(key, value)
    }
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  api(project(":workflow-runtime"))
}
