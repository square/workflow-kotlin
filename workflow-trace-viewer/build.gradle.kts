import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  id("kotlin-multiplatform")
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  jvm()

  sourceSets {
    jvmMain {
      dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.ui)
        implementation(compose.components.resources)
        implementation(compose.components.uiToolingPreview)
        implementation(libs.androidx.lifecycle.viewmodel.core)
        implementation(libs.androidx.lifecycle.compose)
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.swing)
        implementation(compose.materialIconsExtended)
        implementation(libs.moshi.kotlin)
        implementation("io.github.vinceglb:filekit-dialogs-compose:0.10.0-beta03")
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
        packageVersion = "1.0.0"
        macOS {
          bundleID = "com.squareup.workflow1.traceviewer"
        }
      }
    }
  }
}
