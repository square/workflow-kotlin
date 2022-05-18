# workflow-config

## workflow-config:config-jvm

Configuration for the Runtime while running on the JVM. This allows one to specify a project
property from the gradle build to choose a runtime for JVM tests.

e.g. add "-Pworkflow.runtime=timeout".

Note that this will only work for jvm based tests, use config-android for applications and
application tests.

If anything other than the `WorkflowTestRuntime` is used then the config will need to be fetched
via the utility provided:

`val runtimeConfig = JvmTestRuntimeConfigTools.getTestRuntimeConfig()`

## workflow-config:config-android

Configuration for the Workflow Runtime when building an application.

Add "-Pworkflow.runtime=timeout" to the gradle command for building the app.

Your application will also need to fetch the configured `RuntimeConfig` and pass it to
`renderWorkflowIn` or `renderAsState`. You can fetch it with the utility provided:

`val runtimeConfig = AndroidRuntimeConfigTools.getAppWorkflowRuntimeConfig()`
