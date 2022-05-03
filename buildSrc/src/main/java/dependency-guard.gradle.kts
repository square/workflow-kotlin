import com.android.build.gradle.TestedExtension
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPluginExtension

val configurationNames = when {
  // record the root project's *build* classpath
  project == rootProject -> listOf("classpath")
  // Android variants and their configurations are added later in the configuration phase,
  // so we can't look them up now using the `configurations` property.
  // Instead we can just hard-code "releaseRuntimeClasspath" for any module which has AGP applied.
  // This is actually pretty robust, since if this configuration ever changes,
  // dependency-guard will fail when trying to look it up.
  extensions.findByType<TestedExtension>() != null -> listOf("releaseRuntimeClasspath")
  // If we got here, we're either in an empty "parent" module without a build plugin
  // (and no configurations), or we're in a vanilla Kotlin module.  In this case, we can just look
  // at configuration names.
  else -> configurations
    .map { it.name }
    // anything ending with 'runtimeClasspath' but not 'testRuntimeClasspath'
    .filter { it.matches("^(?!test)\\w*[rR]untimeClasspath$".toRegex()) }
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
