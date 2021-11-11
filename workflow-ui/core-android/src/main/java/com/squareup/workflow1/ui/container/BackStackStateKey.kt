package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Sets a disambiguation prefix used by [BackStackContainer] when managing
 * [androidx.savedstate.SavedStateRegistryOwner]. Allows parent containers
 * to use multiple [BackStackContainer] instances at once.
 */
@OptIn(WorkflowUiExperimentalApi::class)
public fun ViewEnvironment.withBackStackStateKeyPrefix(prefix: String): ViewEnvironment {
  return addBackStackStateKeyPrefix(prefix, this)
}

@OptIn(WorkflowUiExperimentalApi::class)
internal val ViewEnvironment.getBackStackStateKeyPrefix: String
  get() = get(BackStackStateKey)

@OptIn(WorkflowUiExperimentalApi::class)
private object BackStackStateKey : ViewEnvironmentKey<String>(String::class) {
  override val default = ""
}

@OptIn(WorkflowUiExperimentalApi::class)
private fun addBackStackStateKeyPrefix(
  prefix: String,
  environment: ViewEnvironment
): ViewEnvironment {
  val upstreamPrefix = environment.getBackStackStateKeyPrefix
  return environment + (BackStackStateKey to upstreamPrefix + prefix)
}
