plugins {
  `java-library`
  id("kotlin-jvm")
  id("published")
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

dependencies {
  api(project(":workflow-runtime"))
}
