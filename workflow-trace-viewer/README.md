# Workflow Trace Viewer

A Compose for Desktop app that can be used to view Workflow traces.

## Running

It can be run via Gradle using:

```shell
./gradlew :workflow-trace-viewer:run
```

By Default, the app will be in file parsing mode, where you are able to select a previously recorded workflow trace file for it to visualize the data. 

By hitting the bottom switch, you are able to toggle to live stream mode, where data is directly pulled from the emulator into the visualizer. 

It is ***important*** to run the emulator first before toggling to live mode.

### Terminology

**Trace**: A trace is a file — made up of frames — that contains the execution history of a Workflow. It includes information about render passes, how states have changed within workflows, and the specific props being passed through. The data collected to generate these should be in chronological order, and allows developers to step through the process easily.

**Frame**: Essentially a "snapshot" of the current "state" of the whole Workflow tree. It contains relevant information about the changes in workflow states and how props are passed throughout.

- Note that "snapshot" and "state" are different from `snapshotState` and `State`, which are idiomatic to the Workflow library.

### External Libraries

[FileKit](https://github.com/vinceglb/FileKit) is an external library made to apply file operations on Kotlin and KMP projects. It's purpose in this app is to allow developers to upload their own json trace files. The motivation for its use is to quickly implement a file picker.
