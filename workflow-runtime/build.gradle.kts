import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-multiplatform`
  published
  id("org.jetbrains.kotlinx.benchmark")
}

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
            implementation(libs.kotlinx.benchmark.runtime)

            implementation(main.compileDependencyFiles + main.output.classesDirs)
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
        api(libs.kotlinx.coroutines.core)

        api(project(":workflow-core"))
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlin.test.jdk)
        implementation(libs.kotlinx.coroutines.test.common)
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
