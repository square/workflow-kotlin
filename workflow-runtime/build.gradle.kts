import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64
import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation

plugins {
  `kotlin-multiplatform`
  published
  id("org.jetbrains.kotlinx.benchmark")
}

kotlin {
  val targets = project.findProperty("workflow.targets") ?: "kmp"
  if (targets == "kmp" || targets == "ios") {
    iosWithSimulatorArm64(project)
  }
  if (targets == "kmp" || targets == "jvm") {
    jvm {
      compilations {
        val main by getting

        create("workflowNode") {
          val workflowNodeCompilation: KotlinJvmCompilation = this
          kotlinOptions {
            // Associating compilations allows us to access declarations with `internal` visibility.
            // It's the new version of the "-Xfriend-paths=___" compiler argument.
            // https://youtrack.jetbrains.com/issue/KTIJ-7662/IDE-support-internal-visibility-introduced-by-associated-compilations
            workflowNodeCompilation.associateWith(main)
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
  if (targets == "kmp" || targets == "js") {
    js { browser() }
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
