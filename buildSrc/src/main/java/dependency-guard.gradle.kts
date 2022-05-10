import com.android.build.gradle.TestedExtension
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPluginExtension

// We have to use `afterEvaluate { ... }` because both KMP and AGP create their configurations later
// in the configuration phase.  If we were to try looking up those configurations eagerly as soon
// as this convention plugin is applied, there would be nothing there.
afterEvaluate {
  val configurationNames = when {
    // record the root project's *build* classpath
    project == rootProject -> listOf("classpath")

    // For Android modules, just hard-code `releaseRuntimeClasspath` for the release variant.
    // This is actually pretty robust, since if this configuration ever changes, dependency-guard
    // will fail when trying to look it up.
    extensions.findByType<TestedExtension>() != null -> listOf("releaseRuntimeClasspath")

    // If we got here, we're either in an empty "parent" module without a build plugin
    // (and no configurations), or we're in a vanilla Kotlin module.  In this case, we can just look
    // at configuration names.
    else -> configurations
      .map { it.name }
      .filter {
        it.endsWith("runtimeClasspath", ignoreCase = true) &&
          !it.endsWith("testRuntimeClasspath", ignoreCase = true)
      }
  }

  if (configurationNames.isNotEmpty()) {
    apply(plugin = "com.dropbox.dependency-guard")

    configure<DependencyGuardPluginExtension> {
      configurationNames.forEach { configName ->
        // Tell dependency-guard to check the `configName` configuration's dependencies.
        configuration(configName)
      }
    }
  }
}
