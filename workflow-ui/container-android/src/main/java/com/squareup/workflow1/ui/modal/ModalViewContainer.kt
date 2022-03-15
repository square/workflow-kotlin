@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.modal

import android.app.Dialog
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_UP
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.annotation.IdRes
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.asScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.container.BackButtonScreen
import com.squareup.workflow1.ui.modal.ModalViewContainer.Companion.binding
import com.squareup.workflow1.ui.onBackPressedDispatcherOwnerOrNull
import com.squareup.workflow1.ui.toView
import kotlin.reflect.KClass

/**
 * Container that shows [HasModals.modals] as arbitrary views in a [Dialog]
 * window. Provides compatibility with
 * [View.backPressedHandler][com.squareup.workflow1.ui.backPressedHandler].
 *
 * Use [binding] to assign particular rendering types to be shown this way.
 */
@WorkflowUiExperimentalApi
public open class ModalViewContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : ModalContainer<Any>(context, attributeSet, defStyle, defStyleRes) {

  /**
   * Called from [buildDialog]. Builds (but does not show) the [Dialog] to
   * display a [view] built via [ViewRegistry].
   *
   * Subclasses may override completely to build their own kind of [Dialog],
   * there is no need to call `super`.
   */
  public open fun buildDialogForView(view: View): Dialog {
    return Dialog(context).apply {
      setCancelable(false)
      setContentView(view)

      // Dialog is sized to wrap the view. Note that this call must come after
      // setContentView.
      window!!.setLayout(WRAP_CONTENT, WRAP_CONTENT)

      // If we don't set or clear the background drawable, the window cannot go full bleed.
      window!!.setBackgroundDrawable(null)
    }
  }

  final override fun buildDialog(
    initialModalRendering: Any,
    initialViewEnvironment: ViewEnvironment
  ): DialogRef<Any> {
    // Put a no-op backPressedHandler behind the given rendering, to
    // ensure that the `onBackPressed` call below will not leak up to handlers
    // that should be blocked by this modal session.
    val wrappedRendering = BackButtonScreen(asScreen(initialModalRendering)) { }

    val viewHolder = wrappedRendering.toView(
      initialViewEnvironment = initialViewEnvironment,
      contextForNewView = this.context,
      container = this
    )

    return buildDialogForView(viewHolder.view)
      .apply {
        // Dialogs are modal windows and so they block events, including back button presses
        // -- that's their job! But we *want* the Activity's onBackPressedDispatcher to fire
        // when back is pressed, so long as it doesn't look past this modal window for handlers.
        //
        // Here, we handle the ACTION_UP portion of a KEYCODE_BACK key event, and below
        // we make sure that the root view has a backPressedHandler that will consume the
        // onBackPressed call if no child of the root modal view does.

        setOnKeyListener { _, keyCode, keyEvent ->
          if (keyCode == KeyEvent.KEYCODE_BACK && keyEvent.action == ACTION_UP) {
            viewHolder.view.context.onBackPressedDispatcherOwnerOrNull()
              ?.onBackPressedDispatcher
              ?.let {
                if (it.hasEnabledCallbacks()) it.onBackPressed()
              }
            true
          } else {
            false
          }
        }
      }
      .run {
        DialogRef(initialModalRendering, initialViewEnvironment, this, viewHolder)
      }
  }

  override fun updateDialog(dialogRef: DialogRef<Any>) {
    with(dialogRef) {
      // Have to preserve the wrapping done in buildDialog. (We can't put the
      // BackButtonScreen in the DialogRef because the superclass needs to be
      // able to do compatibility checks against it when deciding whether
      // or not to update the existing dialog.)
      val wrappedRendering = BackButtonScreen(asScreen(modalRendering)) { }
      @Suppress("UNCHECKED_CAST")
      (extra as ScreenViewHolder<Screen>).show(wrappedRendering, viewEnvironment)
    }
  }

  @PublishedApi
  internal class ModalViewFactory<H : HasModals<*, *>>(
    @IdRes id: Int,
    type: KClass<H>
  ) : com.squareup.workflow1.ui.ViewFactory<H>
  by BuilderViewFactory(
    type = type,
    viewConstructor = { initialRendering, initialEnv, context, _ ->
      ModalViewContainer(context).apply {
        this.id = id
        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        bindShowRendering(initialRendering, initialEnv, ::update)
      }
    }
  )

  public companion object {
    /**
     * Creates a [ViewFactory][com.squareup.workflow1.ui.ViewFactory] for
     * modal container screens of type [H].
     *
     * Each view created for [HasModals.modals] will be shown in a [Dialog]
     * whose window is set to size itself to `WRAP_CONTENT` (see [android.view.Window.setLayout]).
     *
     * @param id a unique identifier for containers of this type, allowing them to participate
     * view persistence
     */
    public inline fun <reified H : HasModals<*, *>> binding(
      @IdRes id: Int = View.NO_ID
    ): com.squareup.workflow1.ui.ViewFactory<H> =
      ModalViewFactory(id, H::class)
  }
}
