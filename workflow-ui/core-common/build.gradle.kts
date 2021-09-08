plugins {
  `java-library`
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
}

kotlin {
  jvm {
    withJava()
  }
  iosX64()
  sourceSets {
    all {
      languageSettings.apply {
        useExperimentalAnnotation("kotlin.RequiresOptIn")
        useExperimentalAnnotation("com.squareup.workflow1.ui.WorkflowUiExperimentalApi")
      }
    }
    val jvmMain by getting {
      dependencies {
        api(Dependencies.Kotlin.Stdlib.jdk6)
        api(Dependencies.okio)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(Dependencies.Kotlin.Test.jdk)
        implementation(Dependencies.Test.truth)
      }
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))
