plugins {
  `java-gradle-plugin`
  id("kotlin-jvm")
  id("published")
}

gradlePlugin {
  plugins {
    create("ai-context") {
      id = "com.squareup.workflow1.ai-context"
      implementationClass = "com.squareup.workflow1.ai.context.AiContextPlugin"
      displayName = "Workflow AI Context"
      description = "Extracts AI context (AGENTS.md, skills) from workflow-kotlin JARs."
    }
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
