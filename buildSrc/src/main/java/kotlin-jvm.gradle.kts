import com.squareup.workflow1.buildsrc.kotlinCommonSettings

plugins {
  kotlin("jvm")
}

extensions.getByType(JavaPluginExtension::class).apply {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Test> {
  project
    .properties
    .asSequence()
    .filter { (key, value) ->
      key.startsWith("workflow.runtime") && value != null
    }
    .forEach { (key, value) ->
      // Add in a system property to the fork for the test.
      systemProperty(key, value!!)
    }
}


project.kotlinCommonSettings(bomConfigurationName = "implementation")
