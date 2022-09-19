import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64
import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-multiplatform`
  published
  id("org.jetbrains.kotlinx.benchmark")
}

kotlin {
  iosWithSimulatorArm64()
  jvm {
    compilations {
      val main by getting
      val workflowNode by creating {
        kotlinOptions {
          val compileKotlinJvm: KotlinCompile by tasks
          freeCompilerArgs += "-Xfriend-paths=${compileKotlinJvm.destinationDirectory}"
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
}

dependencies {
  commonMainApi(project(":workflow-core"))
  commonMainApi(libs.kotlinx.coroutines.core)

  commonTestImplementation(libs.kotlinx.coroutines.test.common)
  commonTestImplementation(libs.kotlin.test.jdk)
}

benchmark {
  targets {
    register("jvmWorkflowNode") {
      this as JvmBenchmarkTarget
      jmhVersion = libs.versions.jmh.get()
    }
  }
}
