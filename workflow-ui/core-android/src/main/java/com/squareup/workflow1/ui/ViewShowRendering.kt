package com.squareup.workflow1.ui

import android.view.View

/**
 * Returns the most recent [Screen] rendering [shown][ScreenViewHolder.show] in this view,
 * or throws a [NullPointerException] if the receiver was not created via
 * [ScreenViewFactory.startShowing].
 */
public var View.screen: Screen
  get() = checkNotNull(screenOrNull) { "Expected to find a Screen in tag R.id.workflow_screen" }
  internal set(value) = setTag(R.id.workflow_screen, value)

/**
 * Returns the most recent [Screen] rendering [shown][ScreenViewHolder.show] in this view,
 * or `null` if the receiver was not initialized via [ScreenViewHolder.startShowing].
 */
public val View.screenOrNull: Screen?
  get() = getTag(R.id.workflow_screen) as? Screen

/**
 * Returns the most recent [ViewEnvironment] applied to this view, or null
 * if the receiver was not initialized via [ScreenViewHolder.startShowing].
 */
public val View.environmentOrNull: ViewEnvironment?
  get() = getTag(R.id.workflow_environment) as? ViewEnvironment
