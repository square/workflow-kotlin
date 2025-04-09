package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.Wrapper

/**
 * An [Overlay] built around a root [content] [Screen].
 */
public interface ScreenOverlay<out ContentT : Screen> : Overlay, Wrapper<Screen, ContentT> {
  public override val content: ContentT

  override fun <ContentU : Screen> map(transform: (ContentT) -> ContentU): ScreenOverlay<ContentU>
}
