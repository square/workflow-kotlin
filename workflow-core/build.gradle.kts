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
        useExperimentalAnnotation("com.squareup.workflow1.InternalWorkflowApi")
      }
    }
    val commonMain by getting {
      dependencies {
        api(Dependencies.Kotlin.Stdlib.common)
        api(Dependencies.Kotlin.Coroutines.core)
        // For Snapshot.
        api(Dependencies.okioMultiplatform)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(Dependencies.Kotlin.Test.annotations)
        implementation(Dependencies.Kotlin.Test.common)
      }
    }
    val jvmMain by getting {
      dependencies {
        compileOnly(Dependencies.Annotations.intellij)
        api(Dependencies.Kotlin.Stdlib.jdk6)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(Dependencies.Kotlin.Coroutines.test)
        implementation(Dependencies.Kotlin.Test.jdk)
      }
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))
