plugins {
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
}

group = project.property("GROUP") as String
version = project.property("VERSION_NAME") as String

// mavenPublish {
  // sonatypeHost = "S01"
// }
