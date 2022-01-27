import com.android.build.gradle.TestedExtension
import com.squareup.workflow1.build.dependency
import com.squareup.workflow1.build.libsCatalog

plugins {
  id("android-defaults")
}

configure<TestedExtension> {
  @Suppress("UnstableApiUsage")
  buildFeatures.viewBinding = true
}

dependencies {
  add("implementation", project(":workflow-core"))
  add("implementation", project(":workflow-runtime"))

  add("implementation", project.libsCatalog.dependency("androidx-appcompat"))
  add("implementation", project.libsCatalog.dependency("timber"))
}
