package com.squareup.workflow1.buildsrc.artifacts

import com.squareup.moshi.JsonClass
import java.io.Serializable

/**
 * Models the module-specific properties of published maven artifacts.
 *
 * see (Niklas Baudy's
 * [gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin))
 *
 * @property gradlePath the path of the Gradle project, such as `:workflow-core`
 * @property group The maven "group", which should always be `com.squareup.workflow1`. This is the
 *   `GROUP` property in the Gradle plugin.
 * @property artifactId The maven "module", such as `workflow-core-jvm`. This is the
 *   `POM_ARTIFACT_ID` property in the Gradle plugin.
 * @property description The description of this specific artifact, such as "Workflow Core". This is
 *   the `POM_NAME` property in the Gradle plugin.
 * @property packaging `aar` or `jar`. This is the `POM_PACKAGING` property in the Gradle plugin.
 * @property javaVersion the java version of the artifact (typically 8 or 11). If not set
 *   explicitly, this defaults to the JDK version used to build the artifact.
 * @property publicationName typically 'maven', but other things for KMP artifacts
 */
@JsonClass(generateAdapter = true)
data class ArtifactConfig(
  val gradlePath: String,
  val group: String,
  val artifactId: String,
  val description: String,
  val packaging: String,
  val javaVersion: Int,
  val publicationName: String
) : Serializable {
  val key = "$gradlePath+$publicationName"
}
