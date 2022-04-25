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
