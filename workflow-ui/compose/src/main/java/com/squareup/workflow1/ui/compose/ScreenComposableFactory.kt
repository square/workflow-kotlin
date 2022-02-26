package com.squareup.workflow1.ui.compose

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactoryFinder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.getShowRendering
import com.squareup.workflow1.ui.showRendering
import com.squareup.workflow1.ui.start
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
public interface ScreenComposableFactory<in RenderingT : Screen> : ViewRegistry.Entry<RenderingT> {

  /**
   * The composable content of this factory. This method will be called any time [rendering]
   * or [viewEnvironment] change, wrapped in a [Box][androidx.compose.foundation.layout.Box].
   * It is the Compose-based analogue of
   * [ScreenViewRunner.showRendering][com.squareup.workflow1.ui.ScreenViewRunner.showRendering].
   */
  @Suppress("FunctionName")
  @Composable public fun Content(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  )
}

@WorkflowUiExperimentalApi
public fun <T : Screen> T.toComposableFactory(
  viewEnvironment: ViewEnvironment
): ScreenComposableFactory<T> {
  val entry = viewEnvironment[ViewRegistry].getEntryFor(this::class)

  @Suppress("UNCHECKED_CAST")
  return entry as? ScreenComposableFactory<T>
    ?: viewEnvironment[ScreenViewFactoryFinder]
        .getViewFactoryForRenderingOrNull(viewEnvironment, this)?.asComposableFactory()
    ?: (this as? ComposeScreen<*>)?.let {
      object : ScreenComposableFactory<T> {
        override val type: KClass<in T> get() = error("Unused, this isn't in a ViewRegistry")

        @Composable override fun Content(
          rendering: T,
          viewEnvironment: ViewEnvironment
        ) {
          (rendering as ComposeScreen<*>).Content(viewEnvironment)
        }
      }
    }
    ?: throw IllegalArgumentException(
      "Is this even possible? Failed to find or create a ScreenComposableFactory" +
        "to display $this, and it doesn't implement ComposeScreen. Instead found $entry."
    )

  // TODO Need to add default wrapper types here s.t. they don't force views to be created.
  //  NamedScreen, EnvironmentScreen
}

@WorkflowUiExperimentalApi
private fun <T : Screen> ScreenViewFactory<T>.asComposableFactory(): ScreenComposableFactory<T> {
  return object : ScreenComposableFactory<T> {
    override val type: KClass<in T> get() = error("Unused, this isn't in a ViewRegistry")

    /**
     * This is effectively the logic of [WorkflowViewStub], but translated into Compose idioms.
     * This approach has a few advantages:
     *
     *  - Avoids extra custom views required to host `WorkflowViewStub` inside a Composition. Its trick
     *    of replacing itself in its parent doesn't play nicely with Compose.
     *  - Allows us to pass the correct parent view for inflation (the root of the composition).
     *  - Avoids `WorkflowViewStub` having to do its own lookup to find the correct [ViewFactory], since
     *    we already have the correct one.
     *  - Propagate the current [LifecycleOwner] from [LocalLifecycleOwner] by setting it as the
     *    [ViewTreeLifecycleOwner] on the view.
     *
     * Like `WorkflowViewStub`, this function uses the original [ScreenViewFactory] to create and
     * memoize a [View] to display the [rendering], keeps it updated with the latest [rendering] and
     * [viewEnvironment], and adds it to the composition.
     */
    override fun Content(
      rendering: T,
      viewEnvironment: ViewEnvironment
    ) {
      val lifecycleOwner = LocalLifecycleOwner.current

      AndroidView(
        factory = { context ->
          // We pass in a null container because the container isn't a View, it's a composable. The
          // compose machinery will generate an intermediate view that it ends up adding this to but
          // we don't have access to that.
          buildView(rendering, viewEnvironment, context, container = null)
            .also { view ->
              view.start()

              // Mirrors the check done in Screen.buildView.
              checkNotNull(view.getShowRendering<Any>()) {
                "View.bindShowRendering should have been called for $view, typically by the " +
                  "ScreenViewFactory that created it."
              }

              // Unfortunately AndroidView doesn't propagate this itself.
              ViewTreeLifecycleOwner.set(view, lifecycleOwner)
              // We don't propagate the (non-compose) SavedStateRegistryOwner, or the (compose)
              // SaveableStateRegistry, because currently all our navigation is implemented as
              // Android views, which ensures there is always an Android view between any state
              // registry and any Android view shown as a child of it, even if there's a compose
              // view in between.
            }
        },
        // This function will be invoked every time this composable is recomposed, which means that
        // any time a new rendering or view environment are passed in we'll send them to the view.
        update = { view ->
          view.showRendering(rendering, viewEnvironment)
        }
      )
    }
  }
}
