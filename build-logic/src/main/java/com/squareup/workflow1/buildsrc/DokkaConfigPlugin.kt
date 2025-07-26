package com.squareup.workflow1.buildsrc

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.dokka.gradle.DokkaExtension

class DokkaConfigPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.apply("org.jetbrains.dokka")

    target.extensions.configure(DokkaExtension::class.java) { dokka ->
      dokka.dokkaSourceSets.configureEach { sourceSet ->
        sourceSet.reportUndocumented.set(false)
        sourceSet.skipDeprecated.set(true)

        val sourceSetName = sourceSet.name

        if (target.file("src/$sourceSetName").exists()) {

          val readmeFile = target.file("${target.projectDir}/README.md")
          // If the module has a README, add it to the module's index
          if (readmeFile.exists()) {
            sourceSet.includes.from(readmeFile)
          }

          sourceSet.sourceLink {
            it.localDirectory.set(target.file("src/$sourceSetName"))

            val modulePath = target.projectDir.relativeTo(target.rootDir).path

            // URL showing where the source code can be accessed through the web browser
            it.remoteUrl("https://github.com/square/workflow-kotlin/blob/main/$modulePath/src/$sourceSetName")
            // Suffix which is used to append the line number to the URL. Use #L for GitHub
            it.remoteLineSuffix.set("#L")
          }
        }
        sourceSet.perPackageOption {
          // Will match all .internal packages and sub-packages, regardless of module.
          it.matchingRegex.set(""".*\.internal.*""")
          it.suppress.set(true)
        }
      }
    }
  }
}
