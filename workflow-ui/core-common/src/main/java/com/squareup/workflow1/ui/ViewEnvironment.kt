package com.squareup.workflow1.ui

import com.squareup.workflow1.LocalMap
import com.squareup.workflow1.LocalMapKey

public typealias ViewEnvironmentKey<T> = LocalMapKey<T>

/**
 * Immutable map of values that a parent view can pass down to
 * its children. Allows containers to give descendants information about
 * the context in which they're drawing.
 *
 * @see LocalMap for implementation
 *
 * Calling [Screen.withEnvironment][com.squareup.workflow1.ui.container.withEnvironment]
 * on a [Screen] is the easiest way to customize its environment before rendering it.
 */
@WorkflowUiExperimentalApi
public typealias ViewEnvironment = LocalMap

@WorkflowUiExperimentalApi
public val EmptyViewEnvironment: ViewEnvironment = LocalMap.Companion.EMPTY
