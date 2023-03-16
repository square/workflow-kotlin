package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.Wrapper
import com.squareup.workflow1.ui.backPressedHandler

/**
 * Adds optional back button handling to a [content] rendering, possibly overriding that
 * the wrapped rendering's own back button handler.
 *
 * @param shadow If `true`, [onBackPressed] is set as the
 * [backPressedHandler][android.view.View.backPressedHandler] after
 * the [content] rendering's view is built / updated, effectively overriding it.
 * If false (the default), [onBackPressed] is set afterward, to allow the wrapped rendering to
 * take precedence if it sets a `backPressedHandler` of its own -- the handler provided
 * here serves as a default.
 *
 * @param onBackPressed The function to fire when the device back button
 * is pressed, or null to set no handler -- or clear a handler that was set previously.
 * Defaults to `null`.
 */
@WorkflowUiExperimentalApi
public class BackButtonScreen<C : Screen>(
  public override val content: C,
  public val shadow: Boolean = false,
  public val onBackPressed: (() -> Unit)? = null
) : Wrapper<Screen, C>, AndroidScreen<BackButtonScreen<C>> {
  override fun <D : Screen> map(transform: (C) -> D): BackButtonScreen<D> =
    BackButtonScreen(transform(content), shadow, onBackPressed)

  override val viewFactory: ScreenViewFactory<BackButtonScreen<C>> =
    ScreenViewFactory.forWrapper(
      showWrapperScreen = { view, backButtonScreen, env, showUnwrapped ->
        if (!backButtonScreen.shadow) {
          // Place our handler before invoking innerShowRendering, so that
          // its later calls to view.backPressedHandler will take precedence
          // over ours.
          view.backPressedHandler = backButtonScreen.onBackPressed
        }

        // Show the content Screen.
        showUnwrapped(backButtonScreen.content, env)

        if (backButtonScreen.shadow) {
          // Place our handler after invoking innerShowRendering, so that ours wins.
          view.backPressedHandler = backButtonScreen.onBackPressed
        }
      }
    )

  @Deprecated("Use content", ReplaceWith("content"))
  public val wrapped: C = content
}
