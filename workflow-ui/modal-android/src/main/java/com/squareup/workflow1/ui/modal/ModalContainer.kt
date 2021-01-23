package com.squareup.workflow1.ui.modal

import android.app.Dialog
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewStateFrame
import com.squareup.workflow1.ui.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.compatible
import com.squareup.workflow1.ui.lifecycleOrNull

/**
 * Base class for containers that show [HasModals.modals] in [Dialog] windows.
 *
 * @param ModalRenderingT the type of the nested renderings to be shown in a dialog window.
 */
@WorkflowUiExperimentalApi
public abstract class ModalContainer<ModalRenderingT : Any> @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {

  private val baseViewStub: WorkflowViewStub = WorkflowViewStub(context).also {
    addView(it, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
  }

  private var dialogs: List<DialogRef<ModalRenderingT>> = emptyList()

  protected fun update(
    newScreen: HasModals<*, ModalRenderingT>,
    viewEnvironment: ViewEnvironment
  ) {
    // WorkflowViewStub handles the beneathModals' Lifecycle for us so we don't need to call destroy
    // here if it changes.
    baseViewStub.update(newScreen.beneathModals, viewEnvironment)

    val newDialogs = mutableListOf<DialogRef<ModalRenderingT>>()
    for ((i, modal) in newScreen.modals.withIndex()) {
      newDialogs += if (i < dialogs.size && compatible(dialogs[i].modalRendering, modal)) {
        dialogs[i].copy(modalRendering = modal, viewEnvironment = viewEnvironment)
          .also { updateDialog(it) }
      } else {
        buildDialog(modal, viewEnvironment).also { ref ->
          // Implementations of buildDialog may use ViewRegistry.buildView, which will set the
          // WorkflowLifecycleOwner on the content view, but since we can't rely on that we also
          // set it here. When the views are attached, this will become the parent lifecycle of the
          // one from buildDialog if any, and so we can use our lifecycle to destroy-on-detach the
          // dialog hierarchy.
          ref.dialog.decorView?.let { dialogView ->
            // Must happen before restore call.
            WorkflowLifecycleOwner.installOn(dialogView)

            dialogView.addOnAttachStateChangeListener(
              object : OnAttachStateChangeListener {
                val onDestroy = OnDestroy { ref.dismiss() }
                var lifecycle: Lifecycle? = null
                override fun onViewAttachedToWindow(v: View) {
                  lifecycle = ref.dialog.lifecycleOrNull()
                  // Android makes a lot of logcat noise if it has to close the window for us. :/
                  // https://github.com/square/workflow/issues/51
                  lifecycle?.addObserver(onDestroy)
                }

                override fun onViewDetachedFromWindow(v: View) {
                  lifecycle?.removeObserver(onDestroy)
                  lifecycle = null
                }
              }
            )
          }
          ref.dialog.show()
        }
      }
    }

    (dialogs - newDialogs).forEach { it.dismiss() }
    dialogs = newDialogs
  }

  /**
   * Called to create (but not show) a Dialog to render [initialModalRendering].
   */
  protected abstract fun buildDialog(
    initialModalRendering: ModalRenderingT,
    initialViewEnvironment: ViewEnvironment
  ): DialogRef<ModalRenderingT>

  protected abstract fun updateDialog(dialogRef: DialogRef<ModalRenderingT>)

  override fun onSaveInstanceState(): Parcelable {
    return SavedState(
        super.onSaveInstanceState()!!,
        dialogs.map { it.save() }
    )
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    (state as? SavedState)
        ?.let {
          if (it.dialogBundles.size == dialogs.size) {
            it.dialogBundles.zip(dialogs) { viewState, dialogRef -> dialogRef.restore(viewState) }
          }
          super.onRestoreInstanceState(state.superState)
        }
        ?: super.onRestoreInstanceState(state)
  }

  /**
   * @param extra optional hook to allow subclasses to associate extra data with this dialog,
   * e.g. its content view. Not considered for equality.
   */
  @WorkflowUiExperimentalApi
  protected data class DialogRef<ModalRenderingT : Any>(
    val modalRendering: ModalRenderingT,
    val viewEnvironment: ViewEnvironment,
    val dialog: Dialog,
    val extra: Any? = null
  ) {

    private val viewStateFrame = ViewStateFrame(Named.keyFor(modalRendering))

    internal fun save(): ViewStateFrame {
      return viewStateFrame.apply {
        performSave(dialog.decorView)
      }
    }

    internal fun restore(viewStateFrame: ViewStateFrame) {
      if (Named.keyFor(modalRendering) == viewStateFrame.key) {
        viewStateFrame.loadFrom(viewStateFrame)
        // This wires up the SavedStateRegistry as well as performs the view hierarchy restore.
        viewStateFrame.restoreTo(dialog.decorView!!)
      }
    }

    /**
     * Dismisses the dialog and notifies its [WorkflowLifecycleOwner] that it is going away for
     * good.
     */
    internal fun dismiss() {
      // The dialog's views are about to be detached, and when that happens we want to transition
      // the dialog view's lifecycle to a terminal state even though the parent is probably still
      // alive.
      viewStateFrame.destroyOnDetach()
      dialog.dismiss()
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as DialogRef<*>

      if (dialog != other.dialog) return false

      return true
    }

    override fun hashCode(): Int {
      return dialog.hashCode()
    }
  }

  private class SavedState : BaseSavedState {
    constructor(
      superState: Parcelable?,
      dialogBundles: List<ViewStateFrame>
    ) : super(superState) {
      this.dialogBundles = dialogBundles
    }

    constructor(source: Parcel) : super(source) {
      @Suppress("UNCHECKED_CAST")
      this.dialogBundles = mutableListOf<ViewStateFrame>().apply {
        source.readTypedList(this, ViewStateFrame)
      }
    }

    val dialogBundles: List<ViewStateFrame>

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      super.writeToParcel(out, flags)
      out.writeTypedList(dialogBundles)
    }

    companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel): SavedState =
        SavedState(source)

      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }
}

private class OnDestroy(private val block: () -> Unit) : LifecycleObserver {
  @OnLifecycleEvent(ON_DESTROY)
  fun onDestroy() = block()
}

@WorkflowUiExperimentalApi
private fun Dialog.lifecycleOrNull(): Lifecycle? = decorView?.context?.lifecycleOrNull()

private val Dialog.decorView: View?
  get() = window?.decorView