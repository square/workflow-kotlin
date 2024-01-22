# Tutorial

## Stale Docs Warning

**This tutorial is tied to an older version of Workflow, and relies on API that has been deprecated or deleted.**
The general concepts are the same, and refactoring to the current API is straightforward,
so it is still worthwhile to work through the tutorial in its current state until we find time to update it.
(Track that work [here](https://github.com/square/workflow-kotlin/issues/905)
and [here](https://github.com/square/workflow-kotlin/issues/884).)

Here's a summary of what has changed, and what replaces what:

- Use of `ViewRegistry` is now optional, and rare.
  Have your renderings implement `AndroidScreen` or `ComposeScreen` to avoid it.
- The API for binding a rendering to UI code has changed as follows, and can all
  be avoided if you use `ComposeScreen`:
   - `ViewFactory<in RenderingT : Any>` is replaced by `ScreenViewFactory<in ScreenT : Screen>`.
   -`LayoutRunner<RenderingT : Any>` is replaced by `ScreenViewRunner<in ScreenT : Screen>`.
   - `LayoutRunner.bind` is replaced by `ScreenViewFactory.fromViewBinding`.
- `BackStackScreen` has been moved to package `com.squareup.workflow1.ui.navigation`.
- `EditText.updateText` and `EditText.setTextChangedListener` are replaced by `TextController`

## Overview

Oh hi! Looks like you want build some software with Workflows! It's a bit different from traditional
Android development, so let's go through building a simple little TODO app to get the basics down.

## Layout

The project has both a starting point, as well as an example of the completed tutorial.

To help with the setup, we have created a few helper modules:

- `tutorial-views`: A set of 3 views for the 3 screens we will be building, `Welcome`, `TodoList`,
  and `TodoEdit`.
- `tutorial-base`: This is the starting point to build out the tutorial. It contains layouts that host the views from `TutorialViews` to see how they display.
- `tutorial-final`: This is an example of the completed tutorial - could be used as a reference if
  you get stuck.

## Getting started

To get set up, launch Android Studio and open the `samples/tutorial` folder.

## Tutorial Steps

- [Tutorial 1](Tutorial1.md) - Single view backed by a workflow
- [Tutorial 2](Tutorial2.md) - Multiple views and navigation
- [Tutorial 3](Tutorial3.md) - State throughout a tree of workflows
- [Tutorial 4](Tutorial4.md) - Refactoring
- [Tutorial 5](Tutorial5.md) - Testing
