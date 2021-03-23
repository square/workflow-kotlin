plugins {
  `java-platform`
  `maven-publish`
}

// This won't actually configure the publication to include the platform, since it's not aware
// of platforms (see https://github.com/vanniktech/gradle-maven-publish-plugin/issues/210), but it
// configures the module's group name and version so when we add our own publication manually it
// will match the rest of the modules.
apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  constraints {
    api(project(":workflow-core"))
    api(project(":workflow-runtime"))
    api(project(":workflow-rx2"))
    api(project(":workflow-testing"))
    api(project(":workflow-tracing"))

    api(project(":workflow-ui:backstack-android"))
    api(project(":workflow-ui:backstack-common"))
    api(project(":workflow-ui:core-android"))
    api(project(":workflow-ui:core-common"))
    api(project(":workflow-ui:modal-android"))
    api(project(":workflow-ui:modal-common"))
    api(project(":workflow-ui:radiography"))
  }
}

// Platforms aren't supported by our third-party maven plugin, so we need to configure it ourselves.
// See https://github.com/vanniktech/gradle-maven-publish-plugin/issues/210.
afterEvaluate {
  extensions.getByType<PublishingExtension>().publications {
    // Use the name "maven" for consistency, since that's what the vanniktech plugin uses.
    create<MavenPublication>("maven") {
      from(components["javaPlatform"])
    }
  }
}
