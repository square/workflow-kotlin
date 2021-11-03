package com.squareup.workflow1.ui

@Deprecated("Use AndroidView")
@WorkflowUiExperimentalApi
public interface AndroidViewRendering<V : AndroidViewRendering<V>> {
  /**
   * Used to build instances of [android.view.View] as needed to
   * display renderings of this type.
   */
  public val viewFactory: ViewFactory<V>
}
