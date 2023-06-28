import com.android.build.api.variant.AndroidComponentsExtension
import com.squareup.workflow1.buildsrc.kotlinCommonSettings
import org.gradle.configurationcache.extensions.capitalized

plugins {
  kotlin("android")
}

extensions.getByType(JavaPluginExtension::class).apply {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

project.kotlinCommonSettings(bomConfigurationName = "implementation")

// For every variant which extends the `debug` type, create a new task which generates all the
// artifacts used in the associated `connected____AndroidTest` task.
extensions.configure<AndroidComponentsExtension<*, *, *>>("androidComponents") {
  onVariants(selector().withBuildType("debug")) { variant ->

    val nameCaps = variant.name.capitalized()
    val testTask = "connected${nameCaps}AndroidTest"

    tasks.register("prepare${nameCaps}AndroidTestArtifacts") {
      description = "Creates all artifacts used in `$testTask` without trying to execute tests."

      dependsOn(tasks.getByName(testTask).taskDependencies)
    }
  }
}


/*

macOS-main-build-artifacts-09555650eb7d6a7cc5d80ddfef5d6ea7bcf31c6cacdf093c62360bd678cb852f-3bd2c177794706d31840536092bfe0e2022bde51249e328646cb585e83e3c119-11a8ec8535b59590a882b41cbf4c2a235a2995758775ba8f9d82847ed5f3f87c-470e8ec00b1701c50f78eb15d625d72753e92d468b096562a31ffab8e1d3f1dd
macOS-main-build-artifacts-09555650eb7d6a7cc5d80ddfef5d6ea7bcf31c6cacdf093c62360bd678cb852f-3bd2c177794706d31840536092bfe0e2022bde51249e328646cb585e83e3c119-ea20820e0c811509dddd86ac8a9f53b6d1bd0785caed7898f1ef2d3390cfae19-470e8ec00b1701c50f78eb15d625d72753e92d468b096562a31ffab8e1d3f1dd


 */
