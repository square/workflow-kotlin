plugins {
  application
  kotlin("jvm")
}

application.mainClassName = "com.squareup.sample.helloterminal.MainKt"

dependencies {
  implementation(project(":samples:hello-terminal:terminal-workflow"))
  implementation(project(":workflow-core"))
  implementation(project(":workflow-runtime"))
}
