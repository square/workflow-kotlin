import com.android.build.gradle.TestedExtension
import com.squareup.workflow1.library
import com.squareup.workflow1.libsCatalog
import com.vanniktech.maven.publish.SonatypeHost

plugins {
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  // track all runtime classpath dependencies for anything we ship
  id("dependency-guard")
}

group = project.property("GROUP") as String
version = project.property("VERSION_NAME") as String

mavenPublish {
  sonatypeHost = SonatypeHost.S01
}
