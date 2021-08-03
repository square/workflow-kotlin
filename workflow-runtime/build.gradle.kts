// import me.champeau.gradle.JMHPluginExtension
// import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  kotlin("multiplatform")

  id("org.jetbrains.dokka")
  // Benchmark plugins.
  // id("me.champeau.gradle.jmh")
  // If this plugin is not applied, IntelliJ won't see the JMH definitions for some reason.
  idea
}

kotlin {
  jvm()
  iosX64()
  sourceSets {
    all {
      languageSettings.apply {
        useExperimentalAnnotation("kotlin.RequiresOptIn")
        useExperimentalAnnotation("com.squareup.workflow1.InternalWorkflowApi")
      }
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

// Benchmark configuration.
/*configure<JMHPluginExtension> {
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
}*/

dependencies {
  compileOnly(Dependencies.Annotations.intellij)

  "commonMainApi"(project(":workflow-core"))
  "commonMainApi"(Dependencies.Kotlin.Stdlib.common)

  "commonTestImplementation"(Dependencies.Kotlin.Test.common)
  "commonTestImplementation"(Dependencies.Kotlin.Test.annotations)

  "jvmTestImplementation"(Dependencies.Kotlin.Test.jdk)
  "jvmTestImplementation"(Dependencies.Kotlin.Coroutines.test)

  // "jvmTestImplementation"(Dependencies.Kotlin.reflect)

  // These dependencies will be available on the classpath for source inside src/jmh.
/*  "jmh"(Dependencies.Kotlin.Stdlib.jdk6)
  "jmh"(Dependencies.Jmh.core)
  "jmh"(Dependencies.Jmh.generator)*/
}
