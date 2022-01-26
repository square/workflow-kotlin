
android.buildFeatures.viewBinding = true

dependencies {
  implementation(project(":workflow-core"))
  implementation(project(":workflow-runtime"))

  implementation(Deps.get("androidx.appcompat"))
  implementation(Deps.get("timber"))
}
