# Workflow Trace Viewer

A Compose for Desktop application that visualizes and debugs Workflow execution traces. This tool
helps developers understand the hierarchical structure and execution flow of their Workflow
applications by providing both file-based and live streaming trace visualization.

## Running

It can be run via Gradle using:

```shell
./gradlew :workflow-trace-viewer:run
```

## Usage Guide

By default, the app will be in file parsing mode, where you are able to select a previously recorded
workflow trace file for it to visualize the data. Once the workflow tree is rendered
in [File](#file-mode) or [Live](#live-mode) mode, you can switch frames (
see [Terminology](#Terminology)) to see different events that fired. All nodes are color coded based
on what had happened during this frame, and a text diff will show the specific changes. You can open
the right node panel and left click a box get a more detailed view of the specific node, or right
click to expand/collapse a specific node's children.

<img src="https://github.com/square/workflow-kotlin/raw/wenli/improve-visualizer/workflow-trace-viewer/docs/demo.gif" width="320" alt="Demo" />

### File Mode

Once a file of the live data is saved, it can easily be uploaded to retrace the steps taken during
the live session. Currently, text/json files that are saved from recordings only contain raw data,
meaning it is simply a list of lists of node renderings.

<img src="https://github.com/square/workflow-kotlin/raw/wenli/improve-visualizer/workflow-trace-viewer/docs/file_mode.gif" width="320" alt="File Mode" />

### Live Mode

By hitting the bottom switch, you are able to toggle to live stream mode, where data is directly
pulled from the emulator into the visualizer. To do so:

- Start the app (on any device)
- Start the app, and toggle the switch to enter Live mode
- Select the desired device

Once in Live mode, frames will appear as you interact with the app. You may also save the current
data into a file saved in `~/Downloads` to be used later (this action will take some time, so it may
not appear immediately)

Render pass data is passively stored in a buffer before being sent to the visualizer, so you do not
need to immediately open/run the app to "catch" everything. However, since the the buffer has
limited size, it's strongly recommended to avoid interacting with the app — beyond starting it —
before Live mode has been triggered; this helps to avoid losing data.

<img src="https://github.com/square/workflow-kotlin/raw/wenli/improve-visualizer/workflow-trace-viewer/docs/live_mode.gif" width="320" alt="Live Mode" />

### Note

A connection can only happen once. There is currently no support for a recording of the trace data
due to the fact that an open socket will consume all render pass data when a connection begins. To
restart the recording:

- (optional) Save the current trace
- Switch out of Live mode
- Restart the app
- Switch back to Live mode, and the

### Terminology

`Trace`: A trace is a file — made up of frames — that contains the execution history of a Workflow.
It includes information about render passes, how states have changed within workflows, and the
specific props being passed through.

`Frame`: Essentially a "snapshot" of the current "state" of the whole Workflow tree. It contains
relevant information about the changes in workflow states and how props are passed throughout.

- Note that "snapshot" and "state" are different from `snapshotState` and `State`, which are
  idiomatic to the Workflow library.

### External Libraries

[FileKit](https://github.com/vinceglb/FileKit) is an external library made to apply file operations
on Kotlin and KMP projects. This simplified the development process of allowing file selection

## Future

This app can be integrated into the process of anyone working with Workflow, so it's highly
encouraged for anyone to make improvements that makes their life a little easier using this app.
