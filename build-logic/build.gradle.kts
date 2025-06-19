plugins {
  alias(libs.plugins.google.ksp)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.ktlint)
  id("java-gradle-plugin")
}

repositories {
  mavenCentral()
  google()
  maven("https://plugins.gradle.org/m2/")
}

gradlePlugin {
  plugins {
    create("android-defaults") {
      id = "android-defaults"
      implementationClass = "com.squareup.workflow1.buildsrc.AndroidDefaultsPlugin"
    }
    create("android-sample-app") {
      id = "android-sample-app"
      implementationClass = "com.squareup.workflow1.buildsrc.AndroidSampleAppPlugin"
    }
    create("android-ui-tests") {
      id = "android-ui-tests"
      implementationClass = "com.squareup.workflow1.buildsrc.AndroidUiTestsPlugin"
    }
    create("artifacts-check") {
      id = "artifacts-check"
      implementationClass = "com.squareup.workflow1.buildsrc.artifacts.ArtifactsPlugin"
    }
    create("compose-ui-tests") {
      id = "compose-ui-tests"
      implementationClass = "com.squareup.workflow1.buildsrc.ComposeUiTestsPlugin"
    }
    create("dependency-guard") {
      id = "dependency-guard"
      implementationClass = "com.squareup.workflow1.buildsrc.DependencyGuardConventionPlugin"
    }
    create("kotlin-android") {
      id = "kotlin-android"
      implementationClass = "com.squareup.workflow1.buildsrc.KotlinAndroidConventionPlugin"
    }
    create("kotlin-jvm") {
      id = "kotlin-jvm"
      implementationClass = "com.squareup.workflow1.buildsrc.KotlinJvmConventionPlugin"
    }
    create("kotlin-multiplatform") {
      id = "kotlin-multiplatform"
      implementationClass = "com.squareup.workflow1.buildsrc.KotlinMultiPlatformConventionPlugin"
    }
    create("published") {
      id = "published"
      implementationClass = "com.squareup.workflow1.buildsrc.PublishingConventionPlugin"
    }
  }
}

dependencies {

  implementation(platform(libs.kotlin.bom))

  compileOnly(gradleApi())

  implementation(libs.burst.plugin)
  implementation(libs.android.gradle.plugin)
  implementation(libs.kgx)
  implementation(libs.dokka.gradle.plugin)
  implementation(libs.dropbox.dependencyGuard)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.squareup.moshi)
  implementation(libs.squareup.moshi.adapters)
  implementation(libs.vanniktech.publish)
  implementation(libs.java.diff.utils)

  ksp(libs.squareup.moshi.codegen)
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.toolchain.get()))
}
