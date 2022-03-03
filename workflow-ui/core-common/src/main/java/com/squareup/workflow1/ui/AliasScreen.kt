package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
public interface AliasScreen : Screen, Compatible {
  public val actual: Screen

  override val compatibilityKey: String get() = Compatible.keyFor(actual)
}

@WorkflowUiExperimentalApi
public tailrec fun Screen.resolve(): Screen {
  return when (this) {
    !is AliasScreen -> this
    else -> actual.resolve()
  }
}
