import me.champeau.gradle.JMHPluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  kotlin("jvm")
  id("org.jetbrains.dokka")
  // Benchmark plugins.
  id("me.champeau.gradle.jmh")
  // If this plugin is not applied, IntelliJ won't see the JMH definitions for some reason.
  idea
  `maven-publish`
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

// Benchmark configuration.
configure<JMHPluginExtension> {
  include = listOf(".*")
  duplicateClassesStrategy = DuplicatesStrategy.WARN
}
configurations.named("jmh") {
  attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
}
tasks.named<KotlinCompile>("compileJmhKotlin") {
  kotlinOptions {
    // Give the benchmark code access to internal definitions.
    val compileKotlin: KotlinCompile by tasks
    freeCompilerArgs += "-Xfriend-paths=${compileKotlin.destinationDir}"
  }
}

dependencies {
  compileOnly(libs.jetbrains.annotations)

  api(project(":workflow-core"))
  api(libs.kotlin.jdk6)
  api(libs.kotlinx.coroutines.core)

  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.kotlin.reflect)

  // These dependencies will be available on the classpath for source inside src/jmh.
  "jmh"(libs.kotlin.jdk6)
  "jmh"(libs.jmh.core)
  "jmh"(libs.jmh.generator)
}
