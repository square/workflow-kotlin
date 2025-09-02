import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("kotlin-multiplatform")
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  jvm()

  jvmToolchain(11)

  sourceSets {
    jvmMain {
      dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.material3)
        implementation(compose.ui)
        implementation(compose.components.resources)
        implementation(compose.components.uiToolingPreview)
        implementation(libs.androidx.lifecycle.viewmodel.core)
        implementation(libs.androidx.lifecycle.compose)
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.swing)
        implementation(compose.materialIconsExtended)
        implementation(libs.squareup.moshi.kotlin)
        implementation(libs.filekit.dialogs.compose)
        implementation(libs.java.diff.utils)
        implementation(libs.telephoto)

        // Add explicit Skiko dependency for current platform
        implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.9.4")
      }
    }
    jvmTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(kotlin("test-junit5"))
      }
    }
  }
}

compose {
  desktop {
    application {
      mainClass = "com.squareup.workflow1.traceviewer.MainKt"
      jvmArgs(
        "-Dapple.awt.application.appearance=system",
      )

      nativeDistributions {
        includeAllModules = true
        targetFormats(TargetFormat.Dmg)
        packageName = "Workflow Trace Viewer"
        packageVersion = (property("VERSION_NAME") as String).substringBefore("-SNAPSHOT")
        macOS {
          bundleID = "com.squareup.workflow1.traceviewer"
        }
      }

      buildTypes.release.proguard {
        isEnabled.set(false)
      }
    }
  }
}

tasks.named<Test>("jvmTest") {
  useJUnitPlatform()
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_11)
  }
}
