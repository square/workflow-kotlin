import com.android.build.gradle.TestedExtension

configure<TestedExtension> {
  buildFeatures.viewBinding = true
}

dependencies {
  add("implementation", project(":workflow-core"))
  add("implementation", project(":workflow-runtime"))

  // implementation(Deps.get("androidx.appcompat"))
  // implementation(Deps.get("timber"))
}
