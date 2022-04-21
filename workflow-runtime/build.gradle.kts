plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  // Benchmark plugins.
  // id("me.champeau.gradle.jmh")
  // // If this plugin is not applied, IntelliJ won't see the JMH definitions for some reason.
  // idea
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

kotlin {
  jvm { withJava() }

  sourceSets {
    val jvmMain by getting {
      dependencies {
        compileOnly(libs.jetbrains.annotations)

        api(project(":workflow-core"))
        api(libs.kotlin.jdk6)
        api(libs.kotlinx.coroutines.core)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.kotlin.test.jdk)
        implementation(libs.kotlin.reflect)
      }
    }
  }
}

// // Benchmark configuration.
// configure<JMHPluginExtension> {
//   include = listOf(".*")
//   duplicateClassesStrategy = DuplicatesStrategy.WARN
// }
// configurations.named("jmh") {
//   attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
// }
// tasks.named<KotlinCompile>("compileJmhKotlin") {
//   kotlinOptions {
//     // Give the benchmark code access to internal definitions.
//     val compileKotlin: KotlinCompile by tasks
//     freeCompilerArgs += "-Xfriend-paths=${compileKotlin.destinationDir}"
//   }
// }

// // These dependencies will be available on the classpath for source inside src/jmh.
// "jmh"(libs.kotlin.jdk6)
// "jmh"(libs.jmh.core)
// "jmh"(libs.jmh.generator)
