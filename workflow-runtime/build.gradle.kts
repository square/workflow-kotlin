import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-multiplatform`
  id("org.jetbrains.dokka")
  id("org.jetbrains.kotlinx.benchmark")
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

kotlin {
  jvm {
    compilations {
      val main by getting
      val workflowNode by creating {
        kotlinOptions {
          val compileKotlinJvm: KotlinCompile by tasks
          freeCompilerArgs += "-Xfriend-paths=${compileKotlinJvm.destinationDir}"
        }
        defaultSourceSet {
          dependencies {
            implementation(main.compileDependencyFiles + main.output.classesDirs)
            implementation(libs.kotlinx.benchmark.runtime)
          }
        }
      }
    }
  }
  ios()

  sourceSets {
    all {
      languageSettings.apply {
        optIn("kotlin.RequiresOptIn")
      }
    }
    val commonMain by getting {
      dependencies {
        api(project(":workflow-core"))
        api(libs.kotlinx.coroutines.core)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlinx.coroutines.test.common)
        implementation(libs.kotlin.test.jdk)
      }
    }
  }
}

benchmark {
  targets {
    register("jvmWorkflowNode") {
      this as JvmBenchmarkTarget
      jmhVersion = libs.versions.jmh.get()
    }
  }
}

val generatedDirPath = "$buildDir/generated/sources/testProperties/kotlin/commonTest"
kotlin.sourceSets {
  val commonTest by getting {
    this.kotlin.srcDir(project.file(generatedDirPath))
  }
}

val generateTestProperties by tasks.registering generator@{

  // pass in a preference from command line via a property argument,
  // like `./gradlew allTests -Pworkflow.myPreference=hello`
  val somePreference = project.findProperty("workflow.myPreference") as? String
    ?: "my-preference-default"

  val testPropertiesDir = File(generatedDirPath)
  val testPropertiesFile = File(testPropertiesDir, "com/squareup/workflow1/TestProperties.kt")

  outputs.file(testPropertiesFile)

  doLast {

    testPropertiesDir.mkdirs()

    testPropertiesFile.writeText(
      """package com.squareup.workflow1
      |
      |public object TestProperties {
      |  const val SOME_PREFERENCE = "$somePreference"
      |}
      |
      """.trimMargin()
    )
  }
}

// make sure that the test properties file is generated before any test compilation
tasks.matching { it is KotlinCompile && it.name.startsWith("compileTest") }
  .all {
    dependsOn(generateTestProperties)
  }
