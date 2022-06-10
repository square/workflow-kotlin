package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * [ViewEnvironment] service object used by [Overlay.toDialogFactory] to find the right
 * [OverlayDialogFactoryScreenViewFactory]. The default implementation makes [AndroidOverlay]
 * work, and provides default bindings for [AlertOverlay] and [FullScreenOverlay].
 *
 * Here is how this hook could be used to provide a custom dialog to handle [FullScreenOverlay]:
 *
 *    class MyDialogFactory : ModalScreenOverlayDialogFactory<ModalScreenOverlay<*>>(
 *      ModalScreenOverlay::class
 *    ) {
 *      override fun buildDialogWithContentView(contentView: View): Dialog {
 *        return super.buildDialogWithContentView(contentView).also {
 *          // Whatever, man, go wild. For that matter don't feel obligated to call super.
 *        }
 *      }
 *    }
 *
 *    object MyFinder: OverlayDialogFactoryFinder {
 *      override fun <OverlayT : Overlay> getDialogFactoryForRendering(
 *        environment: ViewEnvironment,
 *        rendering: OverlayT
 *      ): OverlayDialogFactory<OverlayT> {
 *        if (rendering is ModalScreenOverlay<*>)
 *          return MyDialogFactory as OverlayDialogFactory<OverlayT>
 *        return super.getDialogFactoryForRendering(environment, rendering)
 *      }
 *    }
 *
 *    class MyViewModel(savedState: SavedStateHandle) : ViewModel() {
 *      val renderings: StateFlow<MyRootRendering> by lazy {
 *        val env = ViewEnvironment.EMPTY + (OverlayDialogFactoryFinder to MyFinder)
 *        renderWorkflowIn(
 *          workflow = MyRootWorkflow.mapRenderings { it.withEnvironment(env) },
 *          scope = viewModelScope,
 *          savedStateHandle = savedState
 *        )
 *      }
 *    }
 */
@WorkflowUiExperimentalApi
public interface OverlayDialogFactoryFinder {
  public fun <OverlayT : Overlay> getDialogFactoryForRendering(
    environment: ViewEnvironment,
    rendering: OverlayT
  ): OverlayDialogFactory<OverlayT> {
    val entry = environment[ViewRegistry].getEntryFor(rendering::class)

    @Suppress("UNCHECKED_CAST")
    return entry as? OverlayDialogFactory<OverlayT>
      ?: (rendering as? AndroidOverlay<*>)?.dialogFactory as? OverlayDialogFactory<OverlayT>
      ?: (rendering as? AlertOverlay)?.let {
        AlertOverlayDialogFactory() as OverlayDialogFactory<OverlayT>
      }
      ?: (rendering as? FullScreenOverlay<*>)?.let {
        ScreenOverlayDialogFactory<Screen, FullScreenOverlay<Screen>>(
          FullScreenOverlay::class
        ) as OverlayDialogFactory<OverlayT>
      }
      ?: throw IllegalArgumentException(
        "An OverlayDialogFactory should have been registered to display $rendering, " +
          "or that class should implement AndroidOverlay. Instead found $entry."
      )
  }

  public companion object : ViewEnvironmentKey<OverlayDialogFactoryFinder>(
    OverlayDialogFactoryFinder::class
  ) {
    override val default: OverlayDialogFactoryFinder = object : OverlayDialogFactoryFinder {}
  }
}
