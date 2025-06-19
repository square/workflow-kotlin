# Module Workflow Runtime Android

This module is an Android library that contains utilities to start a Workflow runtime that are
specific to Android components. This contains only the 'headless' components for Workflow on
Android; i.e. no UI concerns.

See :workflow-ui:core-android for the complimentary helpers on Android that include UI concerns:
view model persistent, `WorkflowLayout`, etc.

It also provides a place to include tests that verify behaviour of the runtime while using
Android specific dispatchers.
