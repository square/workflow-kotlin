import com.squareup.workflow1.buildsrc.artifacts.ArtifactsCheckTask
import com.squareup.workflow1.buildsrc.artifacts.ArtifactsDumpTask

check(project.rootProject == project) {
  "Only apply this plugin to the project root."
}

val artifactsDump by tasks.registering(ArtifactsDumpTask::class)

val artifactsCheck by tasks.registering(ArtifactsCheckTask::class)

// Automatically run `artifactsCheck` when running `check`
tasks.named("check") {
  dependsOn(artifactsCheck)
}

// Before any publishing task (local or remote) ensure that the artifacts check is executed.
allprojects {
  tasks.withType(AbstractPublishToMaven::class.java) {
    dependsOn(artifactsCheck)
  }
}
