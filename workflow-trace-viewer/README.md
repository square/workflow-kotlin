# Workflow Trace Viewer

A Compose for Desktop app that can be used to view Workflow traces.

## Running

It can be run via Gradle using:

```shell
./gradlew :workflow-trace-viewer:run
```

### External Libraries

[FileKit](https://github.com/vinceglb/FileKit) is an external library made to apply file operations on Kotlin and KMP projects. It's purpose in this app is to allow developers to upload their own json trace files. The motivation for its use is to quickly implement a file picker.
