import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  id("org.jetbrains.kotlinx.benchmark")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
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
    withJava()
  }

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

benchmark {
  targets {
    register("jvmWorkflowNode") {
      this as JvmBenchmarkTarget
      jmhVersion = libs.versions.jmh.get()
    }
  }
}
